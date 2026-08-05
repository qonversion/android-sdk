package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteConfigSnapshotCoreTest {
    private val scopeA = RemoteConfigSnapshotScope("project", "production", "canonical-user-a")
    private val scopeB = RemoteConfigSnapshotScope("project", "production", "canonical-user-b")
    private val bundled = RemoteConfigScopedBundledRelease(
        projectKey = "project",
        environment = "production",
        release = release("bundle", 1, mapOf("a" to "0", "b" to "0")),
    )
    private val store = RecordingSnapshotStore()
    private val core = RemoteConfigSnapshotCore(store, bundled)

    @Test
    fun `candidate waits for activation and held snapshots never mutate`() {
        core.setScope(scopeA)
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.acceptCandidate(scopeA, release("one", 1, mapOf("a" to "1"))).status,
        )
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
        assertEquals("one", core.lastFetchedSnapshot()?.releaseUid)

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, core.activate().status)
        val held = core.currentSnapshot()
        assertEquals("1", held.rawValue("a")?.value?.decodeToString())

        core.acceptCandidate(scopeA, release("two", 2, mapOf("a" to "2")))
        core.activate()

        assertEquals("2", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
        assertEquals("1", held.rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `one immediate entry activates the entire release and emits one atomic update`() {
        core.setScope(scopeA)
        core.activate()
        val observed = mutableListOf<RemoteConfigSnapshotUpdate>()
        core.addUpdateObserver(observed::add)
        val immediate = release(
            uid = "immediate",
            number = 2,
            values = mapOf("a" to "1", "b" to "2"),
            immediateKey = "a",
        )

        val result = core.acceptCandidate(scopeA, immediate)

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, result.status)
        assertEquals("1", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
        assertEquals("2", core.currentSnapshot().rawValue("b")?.value?.decodeToString())
        assertEquals(1, observed.size)
        assertEquals(setOf("a", "b"), observed.single().changedKeys)
        assertEquals("immediate", observed.single().snapshot.releaseUid)
        assertEquals("immediate", store.states.getValue(scopeA).active?.releaseUid)
    }

    @Test
    fun `logout and identity switch are a hard privacy boundary and late responses are ignored`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("private-a", 1, mapOf("a" to "\"private-a\"")))
        core.activate()

        core.setScope(null)
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
        assertNull(core.lastFetchedSnapshot())

        core.setScope(scopeB)
        val late = core.acceptCandidate(
            scopeA,
            release("late-a", 2, mapOf("a" to "\"must-not-leak\""), immediateKey = "a"),
        )
        assertEquals(RemoteConfigSnapshotTransitionStatus.Ignored, late.status)
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())

        core.setScope(scopeA)
        assertEquals("\"private-a\"", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `candidate commit failure preserves admitted state and emits no update`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("one", 1, mapOf("a" to "1")))
        core.activate()
        val observed = mutableListOf<RemoteConfigSnapshotUpdate>()
        core.addUpdateObserver(observed::add)
        store.failNextSave = true

        val result = core.acceptCandidate(
            scopeA,
            release("two", 2, mapOf("a" to "2"), immediateKey = "a"),
        )

        assertEquals(RemoteConfigSnapshotTransitionStatus.PersistenceFailed, result.status)
        assertEquals("one", core.currentSnapshot().releaseUid)
        assertEquals("one", core.lastFetchedSnapshot()?.releaseUid)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `activation commit failure preserves active candidate and previous without success`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("one", 1, mapOf("a" to "1")))
        core.activate()
        core.acceptCandidate(scopeA, release("two", 2, mapOf("a" to "2")))
        val before = core.currentSnapshot()
        val observed = mutableListOf<RemoteConfigSnapshotUpdate>()
        core.addUpdateObserver(observed::add)
        store.failNextSave = true

        val result = core.activate()

        assertEquals(RemoteConfigSnapshotTransitionStatus.PersistenceFailed, result.status)
        assertFalse(result.changed)
        assertEquals("one", core.currentSnapshot().releaseUid)
        assertEquals("two", core.lastFetchedSnapshot()?.releaseUid)
        assertArrayEquals(before.rawValue("a")?.value, core.currentSnapshot().rawValue("a")?.value)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `older equal and conflicting replays cannot replace freshest candidate`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("newest", 3, mapOf("a" to "3")))

        for (stale in listOf(
            release("older", 2, mapOf("a" to "2"), immediateKey = "a"),
            release("conflict", 3, mapOf("a" to "\"conflict\""), immediateKey = "a"),
        )) {
            assertEquals(
                RemoteConfigSnapshotTransitionStatus.Ignored,
                core.acceptCandidate(scopeA, stale).status,
            )
        }
        assertEquals("newest", core.lastFetchedSnapshot()?.releaseUid)
        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, core.activate().status)
        assertEquals("3", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `last fetched snapshot after activation retains the actual previous decode tier`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("one", 1, mapOf("a" to "{\"value\":1}")))
        core.activate()
        core.acceptCandidate(scopeA, release("two", 2, mapOf("a" to "\"wrong-shape\"")))
        core.activate()

        val resolved = core.lastFetchedSnapshot()?.value("a") { raw ->
            raw.decodeToString().takeIf { it.startsWith("{") }
        }

        assertEquals(RemoteConfigSnapshotValueSource.Cache, resolved?.source)
        assertEquals("{\"value\":1}", resolved?.value)
    }

    @Test
    fun `one failing observer cannot block committed result or other observers`() {
        core.setScope(scopeA)
        val observed = mutableListOf<String>()
        core.addUpdateObserver { error("observer failure") }
        core.addUpdateObserver { update -> observed += update.snapshot.releaseUid }

        val result = core.acceptCandidate(
            scopeA,
            release("immediate", 1, mapOf("a" to "1"), immediateKey = "a"),
        )

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, result.status)
        assertEquals("immediate", core.currentSnapshot().releaseUid)
        assertEquals(listOf("immediate"), observed)
    }

    @Test
    fun `scope change waits for in flight delivery so no private callback arrives after logout`() {
        core.setScope(scopeA)
        val firstObserverStarted = CountDownLatch(1)
        val releaseFirstObserver = CountDownLatch(1)
        val scopeChangeFinished = CountDownLatch(1)
        val callbackAfterLogout = AtomicBoolean(false)
        core.addUpdateObserver {
            firstObserverStarted.countDown()
            releaseFirstObserver.await(2, TimeUnit.SECONDS)
        }
        core.addUpdateObserver {
            if (scopeChangeFinished.count == 0L) callbackAfterLogout.set(true)
        }
        val acceptThread = Thread {
            core.acceptCandidate(
                scopeA,
                release("private", 1, mapOf("a" to "\"private\""), immediateKey = "a"),
            )
        }
        acceptThread.start()
        assertTrue(firstObserverStarted.await(2, TimeUnit.SECONDS))
        val logoutThread = Thread {
            core.setScope(null)
            scopeChangeFinished.countDown()
        }
        logoutThread.start()

        val logoutFinishedBeforeDelivery = scopeChangeFinished.await(250, TimeUnit.MILLISECONDS)
        releaseFirstObserver.countDown()
        acceptThread.join(2_000)
        logoutThread.join(2_000)

        assertFalse(logoutFinishedBeforeDelivery)
        assertFalse(callbackAfterLogout.get())
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `transient scope load failure cannot overwrite durable state and same scope retries`() {
        store.states[scopeA] = RemoteConfigSnapshotState(
            candidate = release("private", 1, mapOf("a" to "\"private\"")),
            active = release("private", 1, mapOf("a" to "\"private\"")),
            didActivate = true,
        )
        store.failNextLoads = 2

        core.setScope(scopeA)
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())

        val activation = core.activate()
        assertEquals(RemoteConfigSnapshotTransitionStatus.PersistenceFailed, activation.status)
        assertTrue(store.savedStates.isEmpty())

        core.setScope(scopeA)
        assertEquals("private", core.currentSnapshot().releaseUid)
        assertEquals("\"private\"", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `activation reports unchanged when a new release has no effective diff`() {
        core.setScope(scopeA)
        val observed = mutableListOf<RemoteConfigSnapshotUpdate>()
        core.addUpdateObserver(observed::add)
        val emptyRelease = RemoteConfigSnapshotRelease(
            releaseUid = "empty",
            releaseNumber = 1,
            manifestContentHash = hash(1),
            entries = emptyList(),
        )
        core.acceptCandidate(scopeA, emptyRelease)

        val result = core.activate()

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, result.status)
        assertFalse(result.changed)
        assertTrue(result.update?.changedKeys?.isEmpty() == true)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `bundled fallback is available only in its project and environment scope`() {
        core.setScope(RemoteConfigSnapshotScope("project", "sandbox", "canonical-user"))
        assertNull(core.currentSnapshot().rawValue("a"))

        core.setScope(RemoteConfigSnapshotScope("other-project", "production", "canonical-user"))
        assertNull(core.currentSnapshot().rawValue("a"))

        core.setScope(scopeA)
        assertEquals("0", core.currentSnapshot().rawValue("a")?.value?.decodeToString())
    }

    @Test
    fun `slow observer never blocks snapshot reads on the state lock`() {
        core.setScope(scopeA)
        val observerStarted = CountDownLatch(1)
        val releaseObserver = CountDownLatch(1)
        val snapshotRead = CountDownLatch(1)
        core.addUpdateObserver {
            observerStarted.countDown()
            releaseObserver.await(2, TimeUnit.SECONDS)
        }
        val acceptThread = Thread {
            core.acceptCandidate(
                scopeA,
                release("private", 1, mapOf("a" to "\"private\""), immediateKey = "a"),
            )
        }
        acceptThread.start()
        assertTrue(observerStarted.await(2, TimeUnit.SECONDS))
        val readerThread = Thread {
            core.currentSnapshot()
            snapshotRead.countDown()
        }
        readerThread.start()

        val readCompletedWhileObserverWasBlocked = snapshotRead.await(250, TimeUnit.MILLISECONDS)
        releaseObserver.countDown()
        acceptThread.join(2_000)
        readerThread.join(2_000)

        assertTrue(readCompletedWhileObserverWasBlocked)
    }

    @Test
    fun `reentrant activation callbacks preserve durable commit order for every observer`() {
        core.setScope(scopeA)
        val observedBySecond = mutableListOf<String>()
        core.addUpdateObserver { update ->
            if (update.snapshot.releaseUid == "one") {
                core.acceptCandidate(
                    scopeA,
                    release("two", 2, mapOf("a" to "2"), immediateKey = "a"),
                )
            }
        }
        core.addUpdateObserver { update -> observedBySecond += update.snapshot.releaseUid }

        core.acceptCandidate(
            scopeA,
            release("one", 1, mapOf("a" to "1"), immediateKey = "a"),
        )

        assertEquals(listOf("one", "two"), observedBySecond)
        assertEquals("two", core.currentSnapshot().releaseUid)
    }

    @Test
    fun `concurrent commit during slow delivery preserves FIFO observer order`() {
        core.setScope(scopeA)
        val firstDeliveryStarted = CountDownLatch(1)
        val releaseFirstDelivery = CountDownLatch(1)
        val secondCommitFinished = CountDownLatch(1)
        val observedBySecond = mutableListOf<String>()
        store.saveObserver = { savedState ->
            if (savedState.active?.releaseUid == "two") secondCommitFinished.countDown()
        }
        core.addUpdateObserver { update ->
            if (update.snapshot.releaseUid == "one") {
                firstDeliveryStarted.countDown()
                releaseFirstDelivery.await(2, TimeUnit.SECONDS)
            }
        }
        core.addUpdateObserver { update -> observedBySecond += update.snapshot.releaseUid }
        val firstThread = Thread {
            core.acceptCandidate(
                scopeA,
                release("one", 1, mapOf("a" to "1"), immediateKey = "a"),
            )
        }
        firstThread.start()
        assertTrue(firstDeliveryStarted.await(2, TimeUnit.SECONDS))
        val secondThread = Thread {
            core.acceptCandidate(
                scopeA,
                release("two", 2, mapOf("a" to "2"), immediateKey = "a"),
            )
        }
        secondThread.start()
        assertTrue(secondCommitFinished.await(2, TimeUnit.SECONDS))

        releaseFirstDelivery.countDown()
        firstThread.join(2_000)
        secondThread.join(2_000)

        assertEquals(listOf("one", "two"), observedBySecond)
        assertEquals("two", core.currentSnapshot().releaseUid)
    }

    private fun release(
        uid: String,
        number: Long,
        values: Map<String, String>,
        immediateKey: String? = null,
    ) = RemoteConfigSnapshotRelease(
        releaseUid = uid,
        releaseNumber = number,
        manifestContentHash = hash(number),
        entries = values.map { (key, value) ->
            RemoteConfigSnapshotEntry.value(
                key = key,
                rawValue = value.encodeToByteArray(),
                variationUid = "$uid-$key",
                applyPolicy = if (key == immediateKey) {
                    RemoteConfigSnapshotApplyPolicy.Immediate
                } else {
                    RemoteConfigSnapshotApplyPolicy.OnNextActivate
                },
                metadata = "{\"release\":\"$uid\"}".encodeToByteArray(),
            )
        },
    )

    private fun hash(number: Long) = number.toString(16).padStart(64, '0')

    private class RecordingSnapshotStore : RemoteConfigSnapshotStore {
        val states = mutableMapOf<RemoteConfigSnapshotScope, RemoteConfigSnapshotState>()
        val savedStates = mutableListOf<RemoteConfigSnapshotState>()
        var failNextSave = false
        var failNextLoads = 0
        var saveObserver: ((RemoteConfigSnapshotState) -> Unit)? = null

        override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult {
            if (failNextLoads > 0) {
                failNextLoads--
                return RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Failed)
            }
            return states[scope]?.let { state ->
                RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, state)
            } ?: RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)
        }

        override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
            if (failNextSave) {
                failNextSave = false
                return false
            }
            states[scope] = state
            savedStates += state
            saveObserver?.invoke(state)
            return true
        }
    }
}
