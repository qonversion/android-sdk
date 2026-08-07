@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The activation ack as the shipped chain produces it: real snapshot core, real read guard, real
 * fetch coordinator, real transport, real HTTP.
 *
 * These tests are about *when* an ack is owed and what it may never cost. The wire contract and the
 * failure ladder are proven in [RemoteConfigActivationAckTest].
 */
internal class RemoteConfigActivationAckIntegrationTest {
    private val harnesses = mutableListOf<RemoteConfigV2Harness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.shutdown() }
    }

    @Test
    fun `an explicit activation is acked exactly once`() {
        val harness = harness()
        harness.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        harness.awaitCandidate(releaseNumber = 1)

        assertTrue(harness.activateBlocking().changed)
        harness.awaitAcks(1)

        val ack = harness.ackRequests.single()
        assertEquals("Bearer project-token", ack.authorization)
        // The very session the snapshot was read under — the ack rides the same credential.
        assertEquals("qrcs1.session-1", ack.sessionHeader)
        assertTrue("unexpected ack body: ${ack.body}", ack.body.startsWith("{\"release_number\":1,\"activated_at\":"))

        // Re-activating the same release owes nothing, however often it is asked for.
        harness.activateBlocking()
        harness.activateBlocking()
        harness.awaitWorkerIdle()
        assertEquals(1, harness.ackRequests.size)
    }

    @Test
    fun `activating a newer release acks it and never re-acks the old one`() {
        val harness = harness()
        harness.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        harness.awaitCandidate(releaseNumber = 1)
        harness.activateBlocking()
        harness.awaitAcks(1)

        harness.serve("release-2", RELEASE_2, listOf(RcWireValue("count", "2")))
        harness.fetchBlocking()
        harness.awaitCandidate(releaseNumber = RELEASE_2)
        assertTrue(harness.activateBlocking().changed)
        harness.awaitAcks(2)

        assertEquals(
            listOf(1L, RELEASE_2),
            harness.ackRequests.map { requireNotNull(RELEASE_NUMBER.find(it.body)).groupValues[1].toLong() },
        )
    }

    @Test
    fun `an activation never waits on the ack`() {
        val harness = harness()
        // The gateway accepts the ack and never answers it.
        harness.hangAckReads(true)
        harness.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        harness.awaitCandidate(releaseNumber = 1)

        // Would time out inside activateBlocking() if activation joined the ack in any way.
        assertTrue(harness.activateBlocking().changed)
        harness.awaitAcks(1)

        // ...and the activation path itself issues no further request: the wedged ack neither
        // blocks nor re-arms anything, and a second activation of the same release is silent.
        assertEquals(1, harness.ackRequests.size)
        harness.activateBlocking()
        harness.awaitWorkerIdle()
        assertEquals(1, harness.ackRequests.size)
    }

    @Test
    fun `a read that implicitly activates is acked too`() {
        // Release builds activate on the first read instead of asserting; that activation changes
        // the served release exactly as an explicit one does.
        val harness = harness(buildMode = RemoteConfigReadBuildMode.Release)
        harness.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        harness.awaitCandidate(releaseNumber = 1)

        assertEquals("1", harness.configs.current.rawValue("count")?.value)
        harness.awaitAcks(1)

        assertTrue(harness.ackRequests.single().body.startsWith("{\"release_number\":1,"))
        // The explicit activate() that follows reports Unchanged and must not ack again.
        harness.activateBlocking()
        harness.awaitWorkerIdle()
        assertEquals(1, harness.ackRequests.size)
    }

    @Test
    fun `an ack a process could not deliver is delivered by the next one`() {
        val snapshotStore = InMemorySnapshotStore()
        val ackStore = InMemoryActivationAckStore()
        val crashed = harness(snapshotStore = snapshotStore, ackStore = ackStore)
        crashed.serveAckStatus(HTTP_SERVICE_UNAVAILABLE)
        crashed.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        crashed.awaitCandidate(releaseNumber = 1)
        crashed.activateBlocking()
        crashed.awaitAcks(1)
        // Walk the bounded retry ladder to its end, so the ack is abandoned in this "process".
        repeat(REMOTE_CONFIG_ACK_MAX_ATTEMPTS - 1) { attempt ->
            awaitScheduledRetry(crashed)
            crashed.awaitAcks(attempt + 2)
        }
        assertEquals(REMOTE_CONFIG_ACK_MAX_ATTEMPTS, crashed.ackRequests.size)
        assertEquals(1, crashed.ackSender.droppedAckCount)

        // A new process over the same durable state.
        val restarted = harness(snapshotStore = snapshotStore, ackStore = ackStore)
        restarted.identify("QON_anon_a", CANONICAL_A, RemoteConfigFetchForceReason.Build)
        restarted.awaitAcks(1)

        assertTrue(restarted.ackRequests.single().body.startsWith("{\"release_number\":1,"))
        assertEquals(0, restarted.ackSender.droppedAckCount)
        // Nothing is owed any more, so a later activation of the same release stays silent.
        restarted.activateBlocking()
        restarted.awaitWorkerIdle()
        assertEquals(1, restarted.ackRequests.size)
    }

    @Test
    fun `an SDK that never learned an identity acks nothing`() {
        val harness = harness()

        harness.activateBlocking()
        harness.awaitWorkerIdle()

        assertTrue(harness.ackRequests.isEmpty())
        assertTrue(harness.sessionRequests.isEmpty())
    }

    private fun awaitScheduledRetry(harness: RemoteConfigV2Harness) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (harness.ackScheduler.pendingCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        harness.ackScheduler.runAll()
    }

    private fun harness(
        buildMode: RemoteConfigReadBuildMode = RemoteConfigReadBuildMode.Debug,
        snapshotStore: InMemorySnapshotStore = InMemorySnapshotStore(),
        ackStore: InMemoryActivationAckStore = InMemoryActivationAckStore(),
    ) = RemoteConfigV2Harness(
        buildMode = buildMode,
        snapshotStore = snapshotStore,
        ackStore = ackStore,
    ).also { harnesses += it }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 10L
        const val CANONICAL_A = "canonical-a"
        const val RELEASE_2 = 2L
        val RELEASE_NUMBER = Regex("\"release_number\":(\\d+)")
    }
}
