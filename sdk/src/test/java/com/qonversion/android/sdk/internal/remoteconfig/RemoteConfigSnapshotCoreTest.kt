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
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest

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
    fun `server release number is a rollback floor while equal follows local admission order`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("newest", 3, mapOf("a" to "3")))

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Ignored,
            core.acceptCandidate(
                scopeA,
                release("rollback", 2, mapOf("a" to "2"), immediateKey = "a"),
            ).status,
        )
        assertEquals("newest", core.lastFetchedSnapshot()?.releaseUid)

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Activated,
            core.acceptCandidate(
                scopeA,
                release("same-number-newest-request", 3, mapOf("a" to "4"), immediateKey = "a"),
            ).status,
        )
        assertEquals("same-number-newest-request", core.currentSnapshot().releaseUid)
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

    @Test
    fun `wire admission rejects one malformed item without any partial durable candidate`() {
        core.setScope(scopeA)
        val malformed = wireBody(
            releaseUid = "wire",
            releaseNumber = 1,
            values = "\"good\":${wireItem("1")},\"bad\":${wireItem("{\"x\":1,\"x\":2}")}",
        )

        val result = core.admitCandidate(
            admissionToken = requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
            body = malformed.encodeToByteArray(),
            etag = strongETag(malformed.encodeToByteArray()),
        )

        assertEquals(RemoteConfigSnapshotTransitionStatus.Rejected, result.status)
        assertNull(core.lastFetchedSnapshot())
        assertTrue(store.savedStates.isEmpty())
    }

    @Test
    fun `wire admission fences exact identity project environment and context scope`() {
        core.setScope(scopeA)
        val body = wireBody("wire", 1, "\"a\":${wireItem("1")}").encodeToByteArray()
        assertNull(core.beginAdmission(scopeB, wireExpectation()))
        assertNull(core.beginAdmission(scopeA, wireExpectation().copy(environmentUid = "staging")))
        for (expectation in listOf(
            wireExpectation().copy(projectId = 43),
            wireExpectation().copy(contextFingerprint = "b".repeat(64)),
        )) {
            val token = requireNotNull(core.beginAdmission(scopeA, expectation))
            val result = core.admitCandidate(token, body, strongETag(body))
            assertEquals(RemoteConfigSnapshotTransitionStatus.Rejected, result.status)
        }
        assertTrue(store.savedStates.isEmpty())
    }

    @Test
    fun `complete wire admission tombstones missing active keys and never resurrects Previous`() {
        core.setScope(scopeA)
        core.acceptCandidate(scopeA, release("one", 1, mapOf("removed" to "{\"shape\":1}")))
        core.activate()
        val body = wireBody(
            releaseUid = "wire-two",
            releaseNumber = 2,
            values = "\"kept\":${wireItem("2", immediate = true)}",
        ).encodeToByteArray()

        val result = core.admitCandidate(
            requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
            body,
            strongETag(body),
        )

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, result.status)
        val admitted = store.states.getValue(scopeA).active
        assertTrue(admitted?.entry("removed")?.isTombstone == true)
        assertNull(core.currentSnapshot().value("removed") { raw -> raw.decodeToString() })
        assertEquals("2", core.currentSnapshot().rawValue("kept")?.value?.decodeToString())
        assertArrayEquals(body, admitted?.canonicalBodyBytes)
        assertEquals(strongETag(body), admitted?.strongETag)
    }

    @Test
    fun `wire admission rejects a parsed response after scope changes away and back`() {
        val raceStore = RecordingSnapshotStore()
        val body = wireBody("wire", 1, "\"a\":${wireItem("1")}").encodeToByteArray()
        val etag = strongETag(body)
        val expectation = wireExpectation()
        val parsed = requireNotNull(RemoteConfigSnapshotEnvelopeParser().parse(body, etag, expectation))
        val parserStarted = CountDownLatch(1)
        val releaseParser = CountDownLatch(1)
        val blockingParser = RemoteConfigSnapshotEnvelopeDecoder { _, _, _ ->
            parserStarted.countDown()
            releaseParser.await(2, TimeUnit.SECONDS)
            parsed
        }
        val raceCore = RemoteConfigSnapshotCore(raceStore, bundled, blockingParser)
        raceCore.setScope(scopeA)
        val admissionToken = requireNotNull(raceCore.beginAdmission(scopeA, expectation))
        val result = AtomicReference<RemoteConfigSnapshotTransitionResult>()
        val admissionThread = Thread {
            result.set(raceCore.admitCandidate(admissionToken, body, etag))
        }
        admissionThread.start()
        assertTrue(parserStarted.await(2, TimeUnit.SECONDS))

        raceCore.setScope(scopeB)
        raceCore.setScope(scopeA)
        releaseParser.countDown()
        admissionThread.join(2_000)

        assertEquals(RemoteConfigSnapshotTransitionStatus.Rejected, result.get()?.status)
        assertTrue(raceStore.savedStates.isEmpty())
    }

    @Test
    fun `admission token is opaque to another core and carries its original expectation`() {
        val firstStore = RecordingSnapshotStore()
        val secondStore = RecordingSnapshotStore()
        val firstCore = RemoteConfigSnapshotCore(firstStore, bundled)
        val secondCore = RemoteConfigSnapshotCore(secondStore, bundled)
        firstCore.setScope(scopeA)
        secondCore.setScope(scopeA)
        val token = requireNotNull(firstCore.beginAdmission(scopeA, wireExpectation()))
        val validBody = wireBody("wire-a", 7, "\"a\":${wireItem("1")}").encodeToByteArray()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            secondCore.admitCandidate(token, validBody, strongETag(validBody)).status,
        )
        assertTrue(secondStore.savedStates.isEmpty())

        val swappedContextBody = wireBody(
            "wire-b",
            7,
            "\"a\":${wireItem("2")}",
            contextFingerprint = "b".repeat(64),
        ).encodeToByteArray()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            firstCore.admitCandidate(token, swappedContextBody, strongETag(swappedContextBody)).status,
        )
        assertTrue(firstStore.savedStates.isEmpty())
    }

    @Test
    fun `only latest issued token can admit and superseded Immediate emits no callback`() {
        val immediateBody = wireBody(
            "superseded-immediate",
            7,
            "\"a\":${wireItem("1", immediate = true)}",
        ).encodeToByteArray()
        val expectation = wireExpectation()
        val parsed = requireNotNull(
            RemoteConfigSnapshotEnvelopeParser().parse(
                immediateBody,
                strongETag(immediateBody),
                expectation,
            ),
        )
        val parserStarted = CountDownLatch(1)
        val releaseParser = CountDownLatch(1)
        val blockingParser = RemoteConfigSnapshotEnvelopeDecoder { _, _, _ ->
            parserStarted.countDown()
            releaseParser.await(2, TimeUnit.SECONDS)
            parsed
        }
        val raceStore = RecordingSnapshotStore()
        val raceCore = RemoteConfigSnapshotCore(raceStore, bundled, blockingParser)
        raceCore.setScope(scopeA)
        val observed = mutableListOf<RemoteConfigSnapshotUpdate>()
        raceCore.addUpdateObserver(observed::add)
        val superseded = requireNotNull(raceCore.beginAdmission(scopeA, expectation))
        val result = AtomicReference<RemoteConfigSnapshotTransitionResult>()
        val admissionThread = Thread {
            result.set(raceCore.admitCandidate(superseded, immediateBody, strongETag(immediateBody)))
        }
        admissionThread.start()
        assertTrue(parserStarted.await(2, TimeUnit.SECONDS))

        requireNotNull(raceCore.beginAdmission(scopeA, expectation))
        releaseParser.countDown()
        admissionThread.join(2_000)

        assertEquals(RemoteConfigSnapshotTransitionStatus.Rejected, result.get()?.status)
        assertTrue(observed.isEmpty())
        assertNull(raceCore.lastFetchedSnapshot())
        assertTrue(raceStore.savedStates.isEmpty())
    }

    @Test
    fun `Immediate committed before a newer admission but delivered after it emits no callback`() {
        val raceStore = RecordingSnapshotStore()
        val raceCore = RemoteConfigSnapshotCore(raceStore, bundled)
        val firstDeliveryStarted = CountDownLatch(1)
        val releaseFirstDelivery = CountDownLatch(1)
        val supersededCommitFinished = CountDownLatch(1)
        val observed = mutableListOf<String>()
        raceStore.saveObserver = { savedState ->
            if (savedState.active?.releaseUid == "superseded") {
                supersededCommitFinished.countDown()
            }
        }
        raceCore.setScope(scopeA)
        raceCore.addUpdateObserver { update ->
            if (update.snapshot.releaseUid == "blocking") {
                firstDeliveryStarted.countDown()
                releaseFirstDelivery.await(2, TimeUnit.SECONDS)
            }
        }
        raceCore.addUpdateObserver { update -> observed += update.snapshot.releaseUid }

        val blockingDeliveryThread = Thread {
            raceCore.acceptCandidate(
                scopeA,
                release("blocking", 1, mapOf("a" to "1"), immediateKey = "a"),
            )
        }
        blockingDeliveryThread.start()
        assertTrue(firstDeliveryStarted.await(2, TimeUnit.SECONDS))

        val supersededBody = wireBody(
            "superseded",
            2,
            "\"a\":${wireItem("2", immediate = true)}",
        ).encodeToByteArray()
        val expectation = wireExpectation()
        val supersededToken = requireNotNull(raceCore.beginAdmission(scopeA, expectation))
        val supersededResult = AtomicReference<RemoteConfigSnapshotTransitionResult>()
        val supersededThread = Thread {
            supersededResult.set(
                raceCore.admitCandidate(
                    supersededToken,
                    supersededBody,
                    strongETag(supersededBody),
                ),
            )
        }
        supersededThread.start()
        assertTrue(supersededCommitFinished.await(2, TimeUnit.SECONDS))

        requireNotNull(raceCore.beginAdmission(scopeA, expectation))
        releaseFirstDelivery.countDown()
        blockingDeliveryThread.join(2_000)
        supersededThread.join(2_000)

        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, supersededResult.get()?.status)
        assertEquals(listOf("blocking"), observed)
        assertEquals("superseded", raceCore.currentSnapshot().releaseUid)
    }

    @Test
    fun `newer request cannot roll server release below candidate or active floor`() {
        core.setScope(scopeA)
        val current = wireBody("release-seven", 7, "\"a\":${wireItem("7")}").encodeToByteArray()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
                current,
                strongETag(current),
            ).status,
        )
        val rollback = wireBody("release-six", 6, "\"a\":${wireItem("6")}").encodeToByteArray()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
                rollback,
                strongETag(rollback),
            ).status,
        )
        assertEquals("release-seven", core.lastFetchedSnapshot()?.releaseUid)

        core.activate()
        val secondRollback = wireBody("release-five", 5, "\"a\":${wireItem("5")}").encodeToByteArray()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
                secondRollback,
                strongETag(secondRollback),
            ).status,
        )
        assertEquals("release-seven", core.currentSnapshot().releaseUid)
    }

    @Test
    fun `same server release can be sequentially admitted for different contexts`() {
        core.setScope(scopeA)
        val firstContext = "a".repeat(64)
        val secondContext = "b".repeat(64)
        val first = wireBody(
            releaseUid = "same-release-first-context",
            releaseNumber = 7,
            values = "\"a\":${wireItem("{\"shape\":1}")}",
            contextFingerprint = firstContext,
        ).encodeToByteArray()
        val second = wireBody(
            releaseUid = "same-release-second-context",
            releaseNumber = 7,
            values = "\"a\":${wireItem("{\"shape\":2}")}",
            contextFingerprint = secondContext,
        ).encodeToByteArray()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation(firstContext))),
                first,
                strongETag(first),
            ).status,
        )
        core.activate()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation(secondContext))),
                second,
                strongETag(second),
            ).status,
        )

        assertEquals("same-release-second-context", core.lastFetchedSnapshot()?.releaseUid)
        assertEquals(secondContext, store.states.getValue(scopeA).candidate?.contextFingerprint)
        assertEquals(7L, store.states.getValue(scopeA).candidate?.releaseNumber)
    }

    @Test
    fun `request start token rejects old response after new and accepts new response after old`() {
        core.setScope(scopeA)
        val oldToken = requireNotNull(core.beginAdmission(scopeA, wireExpectation()))
        val newToken = requireNotNull(core.beginAdmission(scopeA, wireExpectation()))
        val oldBody = wireBody("old", 7, "\"a\":${wireItem("1")}").encodeToByteArray()
        val newBody = wireBody("new", 7, "\"a\":${wireItem("2")}").encodeToByteArray()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(newToken, newBody, strongETag(newBody)).status,
        )
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Rejected,
            core.admitCandidate(oldToken, oldBody, strongETag(oldBody)).status,
        )
        assertEquals("new", core.lastFetchedSnapshot()?.releaseUid)

        val laterToken = requireNotNull(core.beginAdmission(scopeA, wireExpectation()))
        val laterBody = wireBody("later", 7, "\"a\":${wireItem("3")}").encodeToByteArray()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(laterToken, laterBody, strongETag(laterBody)).status,
        )
        assertEquals("later", core.lastFetchedSnapshot()?.releaseUid)

        val orderedCore = RemoteConfigSnapshotCore(store, bundled)
        orderedCore.setScope(scopeB)
        val orderedOldToken = requireNotNull(orderedCore.beginAdmission(scopeB, wireExpectation()))
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            orderedCore.admitCandidate(
                orderedOldToken,
                oldBody,
                strongETag(oldBody),
            ).status,
        )
        val orderedNewToken = requireNotNull(orderedCore.beginAdmission(scopeB, wireExpectation()))
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            orderedCore.admitCandidate(
                orderedNewToken,
                newBody,
                strongETag(newBody),
            ).status,
        )
        assertEquals("new", orderedCore.lastFetchedSnapshot()?.releaseUid)
    }

    @Test
    fun `restart restores admission token high water mark`() {
        core.setScope(scopeA)
        val body = wireBody("wire", 7, "\"a\":${wireItem("1")}").encodeToByteArray()
        val committedToken = requireNotNull(core.beginAdmission(scopeA, wireExpectation()))
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.admitCandidate(committedToken, body, strongETag(body)).status,
        )
        val committedOrdinal = requireNotNull(store.states.getValue(scopeA).candidate).admissionToken

        val restarted = RemoteConfigSnapshotCore(store, bundled)
        restarted.setScope(scopeA)
        val restartedToken = requireNotNull(restarted.beginAdmission(scopeA, wireExpectation()))
        val restartedBody = wireBody("wire-restarted", 7, "\"a\":${wireItem("2")}").encodeToByteArray()
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            restarted.admitCandidate(restartedToken, restartedBody, strongETag(restartedBody)).status,
        )

        assertTrue(requireNotNull(store.states.getValue(scopeA).candidate).admissionToken > committedOrdinal)
    }

    @Test
    fun `immediate same release keeps prior local generation as Previous decode tier`() {
        core.setScope(scopeA)
        val first = wireBody(
            "first",
            7,
            "\"a\":${wireItem("{\"shape\":1}", immediate = true)}",
        ).encodeToByteArray()
        val second = wireBody(
            "second",
            7,
            "\"a\":${wireItem("\"wrong-shape\"", immediate = true)}",
        ).encodeToByteArray()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Activated,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
                first,
                strongETag(first),
            ).status,
        )
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Activated,
            core.admitCandidate(
                requireNotNull(core.beginAdmission(scopeA, wireExpectation())),
                second,
                strongETag(second),
            ).status,
        )

        val resolved = core.currentSnapshot().value("a") { raw ->
            raw.decodeToString().takeIf { it.startsWith("{") }
        }
        assertEquals(RemoteConfigSnapshotValueSource.Cache, resolved?.source)
        assertEquals("{\"shape\":1}", resolved?.value)
        val saved = store.states.getValue(scopeA)
        assertEquals(7L, saved.active?.releaseNumber)
        assertEquals(7L, saved.previous?.releaseNumber)
        assertTrue(requireNotNull(saved.active).admissionToken > requireNotNull(saved.previous).admissionToken)
    }

    private fun wireExpectation(contextFingerprint: String = "a".repeat(64)) =
        RemoteConfigSnapshotEnvelopeExpectation(
        projectId = 42,
        environmentUid = "production",
        contextFingerprint = contextFingerprint,
    )

    private fun wireBody(
        releaseUid: String,
        releaseNumber: Long,
        values: String,
        contextFingerprint: String = "a".repeat(64),
    ) =
        "{\"schema_version\":1,\"project_id\":42,\"environment_uid\":\"production\"," +
            "\"release_uid\":\"$releaseUid\",\"release_number\":$releaseNumber," +
            "\"manifest_content_hash\":\"${hash(releaseNumber)}\",\"complete_key_set\":true," +
            "\"context_fingerprint\":\"$contextFingerprint\",\"values\":{$values}}"

    private fun wireItem(raw: String, immediate: Boolean = false) =
        "{\"raw\":$raw,\"variation_uid\":\"variation-wire\"," +
            "\"apply_policy\":\"${if (immediate) "immediate" else "on_next_activate"}\"," +
            "\"metadata\":null}"

    private fun strongETag(body: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(body)
        .joinToString(prefix = "\"", postfix = "\"", separator = "") { byte -> "%02x".format(byte) }

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
