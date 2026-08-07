@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigApplyPolicy
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigDecoder
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSource
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigActivationCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigFetchCallback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Contract tests for the public Remote Config v2 surface, driven end to end: real snapshot core,
 * real read guard, real fetch coordinator, real HTTP.
 */
internal class QRemoteConfigsPublicApiTest {
    private val harnesses = mutableListOf<RemoteConfigV2Harness>()

    @After
    fun tearDown() {
        harnesses.forEach { it.shutdown() }
    }

    @Test
    fun `fetch times out with the best available snapshot while the request keeps running`() {
        val harness = harness()
        harness.delaySnapshotReads(RESPONSE_DELAY_MILLIS)
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)

        val latch = CountDownLatch(1)
        val result = AtomicReference<QRemoteConfigFetchResult>()
        harness.manager.fetch(FETCH_TIMEOUT_MILLIS) { fetchResult ->
            result.set(fetchResult)
            latch.countDown()
        }
        awaitScheduledTimeout(harness)

        assertTrue("timeout was not delivered", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(listOf(FETCH_TIMEOUT_MILLIS), harness.timeoutScheduler.requestedDelays)
        val timedOut = requireNotNull(result.get())
        assertEquals(QRemoteConfigFetchStatus.TimedOut, timedOut.status)
        // Best available at timeout time: nothing was admitted yet, so the ladder is at fallback.
        val fallback = requireNotNull(timedOut.snapshot.rawValue("count"))
        assertEquals(QRemoteConfigSource.Fallback, fallback.source)
        assertEquals("0", fallback.value)

        // The request was not cancelled — its release is still admitted and activates normally.
        harness.awaitCandidate(releaseNumber = 1)
        assertTrue(harness.activateBlocking().changed)
        assertEquals("1", harness.configs.current.rawValue("count")?.value)
    }

    @Test
    fun `fetch without an explicit timeout uses the configured default`() {
        val harness = harness(defaultFetchTimeoutMillis = FETCH_TIMEOUT_MILLIS)
        harness.hangSnapshotReads(true)
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)

        val latch = CountDownLatch(1)
        val result = AtomicReference<QRemoteConfigFetchResult>()
        harness.manager.fetch(null) { fetchResult ->
            result.set(fetchResult)
            latch.countDown()
        }
        awaitScheduledTimeout(harness)

        assertTrue(latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(QRemoteConfigFetchStatus.TimedOut, requireNotNull(result.get()).status)
        // The default is what was scheduled — a hard-coded or ignored timeout would show up here.
        assertEquals(listOf(FETCH_TIMEOUT_MILLIS), harness.timeoutScheduler.requestedDelays)
    }

    @Test
    fun `activate reports a change only when the activated release differs`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)

        assertEquals(QRemoteConfigFetchStatus.Fetched, harness.fetchBlocking().status)
        val first = harness.activateBlocking()
        val second = harness.activateBlocking()

        assertTrue("the first activation must be a change", first.changed)
        assertFalse("re-activating the same release changes nothing", second.changed)
        assertEquals("release-1", first.snapshot.releaseUid)
        assertNull(first.fetchStatus)
    }

    @Test
    fun `fetchAndActivate carries the fetch status into the activation result`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)

        val latch = CountDownLatch(1)
        val result = AtomicReference<QRemoteConfigActivationResult>()
        // Through the public facade: this overload must not delegate to itself.
        harness.configs.fetchAndActivate { activation ->
            result.set(activation)
            latch.countDown()
        }

        assertTrue(latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        val activation = requireNotNull(result.get())
        assertEquals(QRemoteConfigFetchStatus.Fetched, activation.fetchStatus)
        assertTrue(activation.changed)
        assertEquals("1", activation.snapshot.rawValue("count")?.value)
    }

    @Test
    fun `every public fetch and activate overload completes exactly once`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)

        val fetched = awaitFetch(harness) { callback -> harness.configs.fetch(callback) }
        assertEquals(QRemoteConfigFetchStatus.Fetched, fetched.status)
        val fetchedWithTimeout = awaitFetch(harness) { callback ->
            harness.configs.fetch(TIMEOUT_UNUSED, callback)
        }
        assertEquals(QRemoteConfigFetchStatus.Fetched, fetchedWithTimeout.status)

        val activated = awaitActivation(harness) { callback -> harness.configs.activate(callback) }
        assertTrue(activated.changed)
        assertNull(activated.fetchStatus)
        val reActivated = awaitActivation(harness) { callback ->
            harness.configs.fetchAndActivate(TIMEOUT_UNUSED, callback)
        }
        assertFalse(reActivated.changed)
        assertEquals(QRemoteConfigFetchStatus.Fetched, reActivated.fetchStatus)
    }

    @Test
    fun `a server error is reported as a failed fetch`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.serveStatus(HTTP_SERVER_ERROR)

        assertEquals(QRemoteConfigFetchStatus.Failed, harness.fetchBlocking().status)
    }

    @Test
    fun `an unchanged release is reported as not modified`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.serveStatus(HTTP_NOT_MODIFIED)

        assertEquals(QRemoteConfigFetchStatus.NotModified, harness.fetchBlocking().status)
    }

    @Test
    fun `a fetch inside the minimum interval is throttled`() {
        val harness = RemoteConfigV2Harness(minimumFetchIntervalMillis = THROTTLE_INTERVAL_MILLIS)
            .also { harnesses += it }
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()

        assertEquals(QRemoteConfigFetchStatus.Throttled, harness.fetchBlocking().status)
    }

    @Test
    fun `a rotated targeting context keeps being served, it is not an identity signal`() {
        // The gateway recomputes the fingerprint from mutable inputs — app/OS version, locale,
        // purchases, properties, experiment enrollment — so it changes for the same identity all
        // the time. Refusing the new value would freeze this user's config until logout.
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        assertEquals(QRemoteConfigFetchStatus.Fetched, harness.fetchBlocking().status)
        harness.activateBlocking()

        harness.rotateContextFingerprint()

        assertEquals(QRemoteConfigFetchStatus.Fetched, harness.fetchBlocking().status)
        harness.activateBlocking()
        val served = requireNotNull(harness.configs.current.rawValue("count"))
        assertEquals(QRemoteConfigSource.Server, served.source)
        assertEquals("2", served.value)
        assertEquals("release-rotated", harness.core.lastFetchedSnapshot()?.releaseUid)
    }

    @Test
    fun `a key without metadata reports no metadata rather than the JSON literal`() {
        val harness = harness()
        harness.serve(
            "release-1",
            1,
            listOf(
                RcWireValue("count", "1"),
                RcWireValue("annotated", "2", metadata = "{\"reload\":true}"),
            ),
        )
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val snapshot = harness.configs.current
        assertNull(snapshot.rawValue("count")?.metadataJson)
        assertEquals("{\"reload\":true}", snapshot.rawValue("annotated")?.metadataJson)
    }

    @Test
    fun `a decoder that throws rejects the value like one that returns null`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val thrown = harness.configs.current.value("count") { error("decoder blew up") }

        // No prior release to fall back to and no bundled value the decoder accepts, so the read
        // resolves to nothing instead of propagating the failure to the caller.
        assertNull(thrown)
    }

    @Test
    fun `reads report every ladder position with its value`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val server = requireNotNull(harness.configs.current.value("count", INT_DECODER))
        assertEquals(QRemoteConfigSource.Server, server.source)
        assertEquals(1, server.value)

        val fallback = requireNotNull(harness.configs.current.value("bundled_only", STRING_DECODER))
        assertEquals(QRemoteConfigSource.Fallback, fallback.source)
        assertEquals("\"bundled\"", fallback.value)

        // A release whose value the caller's decoder rejects falls back to the previously
        // activated one — that is the cache position, and only a typed read can observe it.
        harness.serve("release-2", 2, listOf(RcWireValue("count", "\"not-a-number\"")))
        harness.fetchBlocking()
        harness.activateBlocking()

        val cached = requireNotNull(harness.configs.current.value("count", INT_DECODER))
        assertEquals(QRemoteConfigSource.Cache, cached.source)
        assertEquals(1, cached.value)
    }

    @Test
    fun `raw reads stay opaque while typed reads apply the decoder`() {
        val harness = harness()
        harness.serve("release-1", 1, listOf(RcWireValue("count", "{\"nested\":[1,2]}")))
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val snapshot = harness.configs.current
        val raw = requireNotNull(snapshot.rawValue("count"))
        assertEquals("{\"nested\":[1,2]}", raw.value)
        assertEquals(QRemoteConfigSource.Server, raw.source)
        assertEquals(QRemoteConfigApplyPolicy.OnNextActivate, raw.applyPolicy)
        assertEquals("var-count-on_next_activate", raw.variationUid)

        val json = requireNotNull(snapshot.jsonValue("count"))
        @Suppress("UNCHECKED_CAST")
        val nested = (json.value as Map<String, Any?>)["nested"] as List<Any?>
        assertEquals(listOf(1.0, 2.0), nested)

        val typed = requireNotNull(snapshot.value("count", QRemoteConfigDecoder { rawJson -> rawJson.length }))
        assertEquals("{\"nested\":[1,2]}".length, typed.value)
        assertNull(snapshot.rawValue("unknown-key"))
        assertEquals(setOf("count", "bundled_only"), snapshot.contextKeys)
    }

    @Test
    fun `bundled fallback values answer before any fetch or activation`() {
        val harness = harness()

        // No identity, no fetch, no activation: the bundled getter is a pure asset read.
        assertEquals("bundled", harness.configs.fallbackRemoteConfigValue("bundled_only")?.rawValue)
        assertEquals(0.0, harness.configs.fallbackRemoteConfigValue("count")?.rawValue)
        assertNull(harness.configs.fallbackRemoteConfigValue("unknown-key"))

        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.awaitWorkerIdle()
        val preActivate = requireNotNull(harness.configs.current.rawValue("count"))
        assertEquals(QRemoteConfigSource.Fallback, preActivate.source)
    }

    @Test
    fun `reading before activate is reported in a debug build`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.awaitWorkerIdle()

        harness.configs.current
        harness.configs.current

        assertEquals(listOf(REMOTE_CONFIG_READ_BEFORE_ACTIVATE_MESSAGE), harness.assertions)
        assertTrue(harness.guardEvents.contains(RemoteConfigReadGuardEvent.ReadBeforeActivate))
    }

    @Test
    fun `a release build silently activates once on the first read`() {
        val harness = harness(buildMode = RemoteConfigReadBuildMode.Release)
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()

        val implicitlyActivated = requireNotNull(harness.configs.current.rawValue("count"))

        assertEquals(QRemoteConfigSource.Server, implicitlyActivated.source)
        assertEquals("1", implicitlyActivated.value)
        assertTrue(harness.assertions.isEmpty())
        assertTrue(harness.guardEvents.contains(RemoteConfigReadGuardEvent.ImplicitActivation))
    }

    @Test
    fun `an immediate release activates the whole release and reaches subscribers`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val updates = Collections.synchronizedList(mutableListOf<QRemoteConfigUpdate>())
        val latch = CountDownLatch(1)
        harness.subscribeCollecting(updates, latch)
        harness.serve(
            "release-2",
            2,
            listOf(
                RcWireValue("count", "5", applyPolicy = "immediate", metadata = "{\"reload\":true}"),
                RcWireValue("extra", "\"new\""),
            ),
        )
        harness.fetchBlocking()

        assertTrue("no update was delivered", latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        val update = updates.single()
        assertEquals(setOf("count", "extra"), update.changedKeys)
        assertEquals(QRemoteConfigApplyPolicy.Immediate, update.applyPolicy("count"))
        assertEquals("{\"reload\":true}", update.metadataJson("count"))
        // The whole release was swapped, not just the immediate key.
        assertEquals("5", harness.configs.current.rawValue("count")?.value)
        assertEquals("\"new\"", harness.configs.current.rawValue("extra")?.value)
        assertEquals("release-2", update.snapshot.releaseUid)
    }

    @Test
    fun `a removed subscription stops receiving updates`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()

        val updates = Collections.synchronizedList(mutableListOf<QRemoteConfigUpdate>())
        val subscription = harness.subscribeCollecting(updates, CountDownLatch(1))
        subscription.remove()
        subscription.remove()

        harness.serve("release-2", 2, listOf(RcWireValue("count", "9")))
        harness.fetchBlocking()
        harness.activateBlocking()

        assertEquals("9", harness.configs.current.rawValue("count")?.value)
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `refreshing targeting re-fetches without dropping the served release`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()
        harness.serve("release-2", 2, listOf(RcWireValue("count", "2")))

        harness.manager.refreshTargeting()
        harness.awaitCandidate(releaseNumber = 2)

        // Same identity: the served release must keep serving until the app activates the new one.
        assertEquals("1", harness.configs.current.rawValue("count")?.value)
        assertTrue(harness.activateBlocking().changed)
        assertEquals("2", harness.configs.current.rawValue("count")?.value)
    }

    @Test
    fun `an identity switch hides the previous snapshot and forces a fresh fetch`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.activateBlocking()
        assertEquals("1", harness.configs.current.rawValue("count")?.value)

        harness.serve("release-9", 9, listOf(RcWireValue("count", "9")))
        harness.identify("QON_anon_b", "canonical-b", RemoteConfigFetchForceReason.Logout)

        // Decision A: the previous identity's release is unreadable the instant the scope switches,
        // without waiting for any background work.
        val afterSwitch = requireNotNull(harness.configs.current.rawValue("count"))
        assertEquals(QRemoteConfigSource.Fallback, afterSwitch.source)
        assertEquals("0", afterSwitch.value)

        harness.awaitCandidate(releaseNumber = 9)
        harness.activateBlocking()
        assertEquals("9", harness.configs.current.rawValue("count")?.value)

        // The old identity's snapshot is still stored under its own scope and never leaks.
        val scopeA = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "canonical-a")
        val scopeB = RemoteConfigSnapshotScope(RC_PROJECT_KEY, RC_ENVIRONMENT, "canonical-b")
        assertEquals(1L, harness.snapshotStore.states[scopeA]?.active?.releaseNumber)
        assertEquals(9L, harness.snapshotStore.states[scopeB]?.active?.releaseNumber)
        // A session is minted per identity: the new uid never replays the previous token.
        assertTrue(harness.sessionRequests.size >= 2)
        assertTrue(harness.sessionRequests.any { it.contains("QON_anon_a") })
        assertTrue(harness.sessionRequests.any { it.contains("QON_anon_b") })
    }

    @Test
    fun `the client context is re-sent for every identity`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        harness.fetchBlocking()
        harness.identify("QON_anon_b", "canonical-b", RemoteConfigFetchForceReason.Logout)
        awaitSnapshotReads(harness, count = 2)

        val installDates = harness.snapshotRequests.map { body ->
            Regex("\"device_installed_at\":(\\d+)").find(body)?.groupValues?.get(1)
        }
        assertTrue("expected snapshot reads for both identities", installDates.size >= 2)
        // That this value is genuinely device-scoped (rather than a constant supplied by this
        // harness) is proven against a real PackageManager in RemoteConfigV2DeviceScopeTest.
        assertEquals(setOf(RC_DEVICE_INSTALLED_AT.toString()), installDates.toSet())
    }

    @Test
    fun `every completion is delivered on the main thread`() {
        val harness = harness()
        harness.identify("QON_anon_a", "canonical-a", RemoteConfigFetchForceReason.Build)
        val threads = Collections.synchronizedList(mutableListOf<String>())
        val updates = Collections.synchronizedList(mutableListOf<QRemoteConfigUpdate>())
        val updateLatch = CountDownLatch(1)
        harness.manager.subscribeOnConfigUpdate { update ->
            threads += Thread.currentThread().name
            updates += update
            updateLatch.countDown()
        }

        // fetchBlocking / activateBlocking assert the callback thread internally.
        harness.fetchBlocking()
        harness.activateBlocking()

        assertTrue(updateLatch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(listOf(RC_MAIN_THREAD_NAME), threads)
        assertEquals(1, updates.size)
    }

    @Test
    fun `a dormant configuration answers NotConfigured and still serves bundled defaults`() {
        val dormant = QRemoteConfigSnapshotsImpl(
            manager = null,
            bundledValueReader = { contextKey ->
                if (contextKey == "bundled_only") {
                    com.qonversion.android.sdk.dto.QRemoteConfigFallbackValue("bundled")
                } else {
                    null
                }
            },
            mainDispatcher = { action -> action() },
        )

        val fetchResult = AtomicReference<QRemoteConfigFetchResult>()
        dormant.fetch { result -> fetchResult.set(result) }
        val activationResult = AtomicReference<QRemoteConfigActivationResult>()
        dormant.fetchAndActivate(TIMEOUT_UNUSED) { result -> activationResult.set(result) }

        assertEquals(QRemoteConfigFetchStatus.NotConfigured, requireNotNull(fetchResult.get()).status)
        assertEquals(
            QRemoteConfigFetchStatus.NotConfigured,
            requireNotNull(activationResult.get()).fetchStatus,
        )
        assertFalse(requireNotNull(activationResult.get()).changed)
        assertTrue(dormant.current.contextKeys.isEmpty())
        assertNull(dormant.current.rawValue("count"))
        assertEquals("bundled", dormant.fallbackRemoteConfigValue("bundled_only")?.rawValue)
        val updates = Collections.synchronizedList(mutableListOf<QRemoteConfigUpdate>())
        val subscription = dormant.subscribeOnConfigUpdate { update -> updates += update }
        subscription.remove()
        subscription.remove()
        assertTrue(updates.isEmpty())
    }

    private fun awaitFetch(
        harness: RemoteConfigV2Harness,
        call: (QonversionRemoteConfigFetchCallback) -> Unit,
    ): QRemoteConfigFetchResult {
        val latch = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<QRemoteConfigFetchResult>())
        call(
            QonversionRemoteConfigFetchCallback { result ->
                results += result
                latch.countDown()
            },
        )
        assertTrue(latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1, results.size)
        return results.single()
    }

    private fun awaitActivation(
        harness: RemoteConfigV2Harness,
        call: (QonversionRemoteConfigActivationCallback) -> Unit,
    ): QRemoteConfigActivationResult {
        val latch = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<QRemoteConfigActivationResult>())
        call(
            QonversionRemoteConfigActivationCallback { result ->
                results += result
                latch.countDown()
            },
        )
        assertTrue(latch.await(RC_AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1, results.size)
        return results.single()
    }

    private fun harness(
        buildMode: RemoteConfigReadBuildMode = RemoteConfigReadBuildMode.Debug,
        defaultFetchTimeoutMillis: Long = 0,
    ) = RemoteConfigV2Harness(
        buildMode = buildMode,
        defaultFetchTimeoutMillis = defaultFetchTimeoutMillis,
    ).also { harnesses += it }

    private fun awaitSnapshotReads(harness: RemoteConfigV2Harness, count: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (harness.snapshotRequests.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun awaitScheduledTimeout(harness: RemoteConfigV2Harness) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RC_AWAIT_SECONDS)
        while (harness.timeoutScheduler.pendingCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        harness.timeoutScheduler.runAll()
    }

    private companion object {
        const val FETCH_TIMEOUT_MILLIS = 50L
        const val RESPONSE_DELAY_MILLIS = 2_000L
        const val POLL_INTERVAL_MILLIS = 10L
        const val TIMEOUT_UNUSED = 5_000L
        const val THROTTLE_INTERVAL_MILLIS = 600_000L

        val INT_DECODER = QRemoteConfigDecoder { rawJson -> rawJson.toIntOrNull() }
        val STRING_DECODER = QRemoteConfigDecoder { rawJson -> rawJson }
    }
}
