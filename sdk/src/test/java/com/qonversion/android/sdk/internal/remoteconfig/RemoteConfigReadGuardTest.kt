package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class RemoteConfigReadGuardTest {
    private val scopeA = RemoteConfigSnapshotScope("project", "production", "user-a")
    private val scopeB = RemoteConfigSnapshotScope("project", "production", "user-b")
    private val bundle = RemoteConfigScopedBundledRelease(
        projectKey = "project",
        environment = "production",
        release = release("bundle", 1, "0"),
    )

    @Test
    fun `release first read commits one pre-persisted activation without read-path IO`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)
        var ready = 0

        guard.transitionScopeBeforeSdkReady(scopeA) { ready++ }
        assertEquals(0, store.loads)
        executor.runAll()
        assertEquals(1, ready)
        assertEquals(1, store.loads)
        assertEquals(1, store.saves)
        val ioBeforeRead = store.loads to store.saves

        val held = guard.currentSnapshot()
        assertEquals("candidate", held.releaseUid)
        assertEquals("2", held.rawValue("key")?.value?.decodeToString())
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals("candidate", guard.currentSnapshot().releaseUid)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ReadBeforeActivate })
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })

        core.acceptCandidate(scopeA, release("next", 3, "3"))
        assertEquals("2", held.rawValue("key")?.value?.decodeToString())
    }

    @Test
    fun `candidate fetched after preload is durably prepared and active on first read`() {
        val active = release("active", 1, "1").withAdmissionToken(1)
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(
                candidate = active,
                active = active,
                didActivate = true,
                latestAdmissionToken = 1,
            )
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.acceptCandidate(scopeA, release("fetched", 2, "2")).status,
        )
        assertEquals("fetched", store.states.getValue(scopeA).active?.releaseUid)
        val ioBeforeRead = store.loads to store.saves

        assertEquals("fetched", guard.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
        assertEquals("fetched", guard.currentSnapshot().releaseUid)
    }

    @Test
    fun `debug first read reports the exact actionable assertion once and never activates`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val executor = ManualExecutor()
        val assertions = mutableListOf<String>()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            core = RemoteConfigSnapshotCore(store, bundle),
            preloader = PersistentRemoteConfigReadPreloader(store, executor),
            mode = RemoteConfigReadBuildMode.Debug,
            assertion = RemoteConfigReadAssertion(assertions::add),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        val ioBeforeRead = store.loads to store.saves

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        assertEquals(
            RemoteConfigSnapshotValueSource.Fallback,
            guard.currentSnapshot().rawValue("key")?.source,
        )
        assertEquals(listOf(REMOTE_CONFIG_READ_BEFORE_ACTIVATE_MESSAGE), assertions)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(0, store.saves)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ReadBeforeActivate })
    }

    @Test
    fun `read before preload readiness pins bundle and late preload cannot change current`() {
        val preloader = ControlledPreloader()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(RecordingStore(), bundle)
        val guard = guard(core, preloader, telemetry = telemetry)
        guard.transitionScopeBeforeSdkReady(scopeA)

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        preloader.complete(
            0,
            readyPreload(
                base = RemoteConfigSnapshotState(candidate = release("private", 2, "\"private\"")),
            ),
        )
        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.PreloadNotReady })
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ReadBeforeActivate })
    }

    @Test
    fun `concurrent first reads perform one implicit attempt and see one committed generation`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, executor),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        val start = CountDownLatch(1)
        val done = CountDownLatch(32)
        val releases = Collections.synchronizedList(mutableListOf<String>())
        repeat(32) {
            Thread {
                start.await()
                releases += guard.currentSnapshot().releaseUid
                done.countDown()
            }.start()
        }

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("candidate"), releases.toSet())
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ReadBeforeActivate })
        assertEquals(1, store.saves)
    }

    @Test
    fun `activation preparation failure preserves old active and is observable`() {
        val old = release("active", 1, "1").withAdmissionToken(1)
        val next = release("candidate", 2, "2").withAdmissionToken(2)
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(
                candidate = next,
                active = old,
                didActivate = true,
                latestAdmissionToken = 2,
            )
            failSaves = true
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, executor),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        val ioBeforeRead = store.loads to store.saves

        assertEquals("active", guard.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ActivationPersistenceFailed })
        assertEquals(0, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `successful fetch rearms first read after initial preparation persistence failure`() {
        val old = release("active", 1, "1").withAdmissionToken(1)
        val pending = release("pending", 2, "2").withAdmissionToken(2)
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(
                candidate = pending,
                active = old,
                didActivate = true,
                latestAdmissionToken = 2,
            )
            failSaves = true
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        store.failSaves = false

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.acceptCandidate(scopeA, release("recovered", 3, "3")).status,
        )
        assertEquals("recovered", store.states.getValue(scopeA).active?.releaseUid)
        val ioBeforeRead = store.loads to store.saves

        assertEquals("recovered", guard.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `implicit activation delivers one observer update outside guard locks`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val executor = ManualExecutor()
        val core = RemoteConfigSnapshotCore(store, bundle)
        lateinit var guard: RemoteConfigReadGuard
        val observed = mutableListOf<String>()
        guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor))
        core.addUpdateObserver { update ->
            observed += update.snapshot.releaseUid
            assertEquals("candidate", guard.currentSnapshot().releaseUid)
        }
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()

        assertEquals("candidate", guard.currentSnapshot().releaseUid)
        assertEquals(listOf("candidate"), observed)
        assertEquals("candidate", guard.currentSnapshot().releaseUid)
        assertEquals(listOf("candidate"), observed)
    }

    @Test
    fun `implicit activation observer can coordinate a concurrent snapshot read`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val executor = ManualExecutor()
        val core = RemoteConfigSnapshotCore(store, bundle)
        lateinit var guard: RemoteConfigReadGuard
        var callbackCouldCoordinate = false
        guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor))
        core.addUpdateObserver {
            val coordinated = CountDownLatch(1)
            Thread {
                if (guard.currentSnapshot().releaseUid == "candidate") coordinated.countDown()
            }.start()
            callbackCouldCoordinate = coordinated.await(1, TimeUnit.SECONDS)
        }
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()

        assertEquals("candidate", guard.currentSnapshot().releaseUid)
        assertTrue(callbackCouldCoordinate)
    }

    @Test
    fun `readiness callback can synchronously coordinate another scope transition`() {
        val executor = ManualExecutor()
        val guard = guard(
            RemoteConfigSnapshotCore(RecordingStore(), bundle),
            PersistentRemoteConfigReadPreloader(RecordingStore(), executor),
        )
        val transitionFinished = CountDownLatch(1)
        var callbackCouldCoordinate = false
        guard.transitionScopeBeforeSdkReady(scopeA) {
            Thread {
                guard.transitionScopeBeforeSdkReady(scopeB)
                transitionFinished.countDown()
            }.start()
            callbackCouldCoordinate = transitionFinished.await(1, TimeUnit.SECONDS)
        }

        executor.runAll()

        assertTrue(callbackCouldCoordinate)
    }

    @Test
    fun `already active restart and immediate fetch never report implicit activation`() {
        val active = release("active", 1, "1").withAdmissionToken(1)
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(
                candidate = active,
                active = active,
                didActivate = true,
                latestAdmissionToken = 1,
            )
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        core.acceptCandidate(scopeA, release("immediate", 2, "2", immediate = true))

        assertEquals("immediate", guard.currentSnapshot().releaseUid)
        assertEquals(0, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `implicit activation opportunity is consumed once for the SDK lifetime across scopes`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate-a", 1, "1"))
            states[scopeB] = RemoteConfigSnapshotState(candidate = release("candidate-b", 2, "2"))
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, executor),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        assertEquals("candidate-a", guard.currentSnapshot().releaseUid)

        guard.transitionScopeBeforeSdkReady(scopeB)
        executor.runAll()

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
        assertEquals(1, store.saves)
    }

    @Test
    fun `read between scope mutation and token binding cannot consume implicit activation`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate-a", 1, "1"))
            states[scopeB] = RemoteConfigSnapshotState(candidate = release("candidate-b", 2, "2"))
        }
        val executor = ManualExecutor()
        val scopeMutated = CountDownLatch(1)
        val releaseBinding = CountDownLatch(1)
        val transitionFinished = CountDownLatch(1)
        val scopeMutationCalls = AtomicInteger()
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(
            store = store,
            bundledRelease = bundle,
            scopePreloadMutatedBeforeBinding = {
                if (scopeMutationCalls.incrementAndGet() == 2) {
                    scopeMutated.countDown()
                    check(releaseBinding.await(5, TimeUnit.SECONDS))
                }
            },
        )
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()

        Thread {
            guard.transitionScopeBeforeSdkReady(scopeB)
            transitionFinished.countDown()
        }.start()
        assertTrue(scopeMutated.await(5, TimeUnit.SECONDS))

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        releaseBinding.countDown()
        assertTrue(transitionFinished.await(5, TimeUnit.SECONDS))
        executor.runAll()
        val ioBeforeRead = store.loads to store.saves

        assertEquals("candidate-b", guard.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `read with null token during initial binding cannot consume implicit activation`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 1, "1"))
        }
        val executor = ManualExecutor()
        val scopeMutated = CountDownLatch(1)
        val releaseBinding = CountDownLatch(1)
        val transitionFinished = CountDownLatch(1)
        val telemetry = RecordingTelemetry()
        val core = RemoteConfigSnapshotCore(
            store = store,
            bundledRelease = bundle,
            scopePreloadMutatedBeforeBinding = {
                scopeMutated.countDown()
                check(releaseBinding.await(5, TimeUnit.SECONDS))
            },
        )
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor), telemetry = telemetry)

        Thread {
            guard.transitionScopeBeforeSdkReady(scopeA)
            transitionFinished.countDown()
        }.start()
        assertTrue(scopeMutated.await(5, TimeUnit.SECONDS))

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        releaseBinding.countDown()
        assertTrue(transitionFinished.await(5, TimeUnit.SECONDS))
        executor.runAll()

        assertEquals("candidate", guard.currentSnapshot().releaseUid)
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `explicit activation consumes the lifetime implicit activation opportunity`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate-a", 1, "1"))
            states[scopeB] = RemoteConfigSnapshotState(candidate = release("candidate-b", 2, "2"))
        }
        val executor = ManualExecutor()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, executor),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, guard.activate().status)

        guard.transitionScopeBeforeSdkReady(scopeB)
        executor.runAll()

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        assertEquals(0, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
        assertEquals(1, store.saves)
    }

    @Test
    fun `prepared activation survives restart without another persistence write`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("candidate", 2, "2"))
        }
        val firstExecutor = ManualExecutor()
        val first = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, firstExecutor),
        )
        first.transitionScopeBeforeSdkReady(scopeA)
        firstExecutor.runAll()
        assertEquals("candidate", first.currentSnapshot().releaseUid)
        assertEquals(1, store.saves)

        val restartedExecutor = ManualExecutor()
        val restartedTelemetry = RecordingTelemetry()
        val restarted = guard(
            RemoteConfigSnapshotCore(store, bundle),
            PersistentRemoteConfigReadPreloader(store, restartedExecutor),
            telemetry = restartedTelemetry,
        )
        restarted.transitionScopeBeforeSdkReady(scopeA)
        restartedExecutor.runAll()
        val ioBeforeRead = store.loads to store.saves

        assertEquals("candidate", restarted.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads to store.saves)
        assertEquals(1, store.saves)
        assertEquals(0, restartedTelemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
    }

    @Test
    fun `late old-scope preload is fenced and can never expose another identity`() {
        val preloader = ControlledPreloader()
        val guard = guard(RemoteConfigSnapshotCore(RecordingStore(), bundle), preloader)
        guard.transitionScopeBeforeSdkReady(scopeA)
        guard.transitionScopeBeforeSdkReady(scopeB)
        val baseB = RemoteConfigSnapshotState(candidate = release("private-b", 2, "\"b\""))
        preloader.complete(1, readyPreload(baseB))

        assertEquals("private-b", guard.currentSnapshot().releaseUid)
        val baseA = RemoteConfigSnapshotState(candidate = release("private-a", 3, "\"a\""))
        preloader.complete(0, readyPreload(baseA))
        assertEquals("private-b", guard.currentSnapshot().releaseUid)
        assertEquals("\"b\"", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
    }

    @Test
    fun `stale same-scope preload cannot overwrite a newer durable admission`() {
        val initial = RemoteConfigSnapshotState(candidate = release("old", 1, "1"))
        val store = InterleavingPreloadStore(scopeA, initial)
        val executor = TrackingExecutor(expectedTasks = 3)
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor))
        val currentPreloadReady = CountDownLatch(1)

        guard.transitionScopeBeforeSdkReady(scopeA)
        assertTrue(store.firstLoadEntered.await(5, TimeUnit.SECONDS))
        guard.transitionScopeBeforeSdkReady(scopeB)
        guard.transitionScopeBeforeSdkReady(scopeA) { currentPreloadReady.countDown() }
        assertTrue(currentPreloadReady.await(5, TimeUnit.SECONDS))
        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.acceptCandidate(scopeA, release("new", 2, "2")).status,
        )
        assertEquals("new", store.currentState().active?.releaseUid)

        store.releaseFirstLoad.countDown()
        assertTrue(executor.finished.await(5, TimeUnit.SECONDS))

        assertEquals("new", store.currentState().active?.releaseUid)
        assertEquals(2, store.saves.get())
        executor.shutdown()
    }

    @Test
    fun `same-generation preload cannot replace a newer durable admission`() {
        val initial = RemoteConfigSnapshotState(candidate = release("old", 1, "1"))
        val store = InterleavingPreloadStore(scopeA, initial)
        val executor = TrackingExecutor(expectedTasks = 1)
        val core = RemoteConfigSnapshotCore(store, bundle)
        val telemetry = RecordingTelemetry()
        val guard = guard(
            core,
            PersistentRemoteConfigReadPreloader(store, executor),
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        assertTrue(store.firstLoadEntered.await(5, TimeUnit.SECONDS))

        assertEquals(
            RemoteConfigSnapshotTransitionStatus.Accepted,
            core.acceptCandidate(scopeA, release("new", 2, "2")).status,
        )
        assertEquals("new", core.lastFetchedSnapshot()?.releaseUid)
        store.releaseFirstLoad.countDown()
        assertTrue(executor.finished.await(5, TimeUnit.SECONDS))

        assertEquals("new", core.lastFetchedSnapshot()?.releaseUid)
        assertEquals("new", store.currentState().candidate?.releaseUid)
        assertEquals(1, store.saves.get())
        val ioBeforeRead = store.loads.get() to store.saves.get()

        assertEquals("new", guard.currentSnapshot().releaseUid)
        assertEquals(ioBeforeRead, store.loads.get() to store.saves.get())
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ImplicitActivation })
        executor.shutdown()
    }

    @Test
    fun `stale preload failure cannot advance readiness for the current scope`() {
        val preloader = ControlledPreloader()
        val readiness = mutableListOf<String>()
        val guard = guard(RemoteConfigSnapshotCore(RecordingStore(), bundle), preloader)
        guard.transitionScopeBeforeSdkReady(scopeA) { readiness += "a" }
        guard.transitionScopeBeforeSdkReady(scopeB) { readiness += "b" }

        preloader.complete(
            0,
            RemoteConfigReadPreloadResult(RemoteConfigReadPreloadStatus.Failed),
        )
        assertTrue(readiness.isEmpty())
        val baseB = RemoteConfigSnapshotState(candidate = release("private-b", 2, "\"b\""))
        preloader.complete(1, readyPreload(baseB))

        assertEquals(listOf("b"), readiness)
        assertEquals("private-b", guard.currentSnapshot().releaseUid)
    }

    @Test
    fun `after first read only explicit activation or immediate whole release changes current`() {
        val store = RecordingStore().apply {
            states[scopeA] = RemoteConfigSnapshotState(candidate = release("one", 1, "1"))
        }
        val executor = ManualExecutor()
        val core = RemoteConfigSnapshotCore(store, bundle)
        val guard = guard(core, PersistentRemoteConfigReadPreloader(store, executor))
        guard.transitionScopeBeforeSdkReady(scopeA)
        executor.runAll()
        assertEquals("one", guard.currentSnapshot().releaseUid)

        core.acceptCandidate(scopeA, release("two", 2, "2"))
        assertEquals("one", guard.currentSnapshot().releaseUid)
        assertEquals(RemoteConfigSnapshotTransitionStatus.Activated, guard.activate().status)
        assertEquals("two", guard.currentSnapshot().releaseUid)

        core.acceptCandidate(scopeA, release("three", 3, "3", immediate = true))
        assertEquals("three", guard.currentSnapshot().releaseUid)
    }

    @Test
    fun `corrupt preload is observable and serves only matching pinned bundle`() {
        val preloader = ControlledPreloader()
        val telemetry = RecordingTelemetry()
        val guard = guard(
            RemoteConfigSnapshotCore(RecordingStore(), bundle),
            preloader,
            telemetry = telemetry,
        )
        guard.transitionScopeBeforeSdkReady(scopeA)
        preloader.complete(
            0,
            RemoteConfigReadPreloadResult(RemoteConfigReadPreloadStatus.Corrupt),
        )

        assertEquals("0", guard.currentSnapshot().rawValue("key")?.value?.decodeToString())
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.PreloadCorrupt })
        assertEquals(1, telemetry.events.count { it == RemoteConfigReadGuardEvent.ReadBeforeActivate })
    }

    private fun guard(
        core: RemoteConfigSnapshotCore,
        preloader: RemoteConfigReadPreloader,
        mode: RemoteConfigReadBuildMode = RemoteConfigReadBuildMode.Release,
        assertion: RemoteConfigReadAssertion = RemoteConfigReadAssertion { throw AssertionError(it) },
        telemetry: RemoteConfigReadTelemetry = RemoteConfigReadTelemetry { },
    ) = RemoteConfigReadGuard(core, preloader, mode, assertion, telemetry)

    private fun readyPreload(base: RemoteConfigSnapshotState): RemoteConfigReadPreloadResult =
        RemoteConfigReadPreloadResult(
            status = RemoteConfigReadPreloadStatus.Ready,
            baseState = base,
            preparedActivationState = base.preparedActivationState(),
        )

    private fun release(uid: String, number: Long, raw: String, immediate: Boolean = false) =
        RemoteConfigSnapshotRelease(
            releaseUid = uid,
            releaseNumber = number,
            manifestContentHash = number.toString(16).padStart(64, '0'),
            entries = listOf(
                RemoteConfigSnapshotEntry.value(
                    key = "key",
                    rawValue = raw.encodeToByteArray(),
                    variationUid = "$uid-key",
                    applyPolicy = if (immediate) {
                        RemoteConfigSnapshotApplyPolicy.Immediate
                    } else {
                        RemoteConfigSnapshotApplyPolicy.OnNextActivate
                    },
                    metadata = null,
                ),
            ),
        )

    private class RecordingStore : RemoteConfigSnapshotStore {
        val states = mutableMapOf<RemoteConfigSnapshotScope, RemoteConfigSnapshotState>()
        var loads = 0
        var saves = 0
        var failSaves = false
        var loadStatus: RemoteConfigSnapshotLoadStatus? = null

        override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult {
            loads++
            loadStatus?.let { return RemoteConfigSnapshotLoadResult(it) }
            return states[scope]?.let {
                RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, it)
            } ?: RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)
        }

        override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
            saves++
            if (failSaves) return false
            states[scope] = state
            return true
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = mutableListOf<Runnable>()
        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeAt(0).run()
        }
    }

    private class TrackingExecutor(expectedTasks: Int) : Executor {
        private val delegate = Executors.newCachedThreadPool()
        val finished = CountDownLatch(expectedTasks)

        override fun execute(command: Runnable) {
            delegate.execute {
                try {
                    command.run()
                } finally {
                    finished.countDown()
                }
            }
        }

        fun shutdown() {
            delegate.shutdownNow()
        }
    }

    private class InterleavingPreloadStore(
        private val scope: RemoteConfigSnapshotScope,
        initial: RemoteConfigSnapshotState,
    ) : RemoteConfigSnapshotStore {
        val firstLoadEntered = CountDownLatch(1)
        val releaseFirstLoad = CountDownLatch(1)
        val saves = AtomicInteger()
        val loads = AtomicInteger()
        private val stateLock = Any()
        private var state = initial

        override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult {
            val captured = synchronized(stateLock) { state }
            if (scope == this.scope && loads.incrementAndGet() == 1) {
                firstLoadEntered.countDown()
                check(releaseFirstLoad.await(5, TimeUnit.SECONDS))
            }
            return if (scope == this.scope) {
                RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, captured)
            } else {
                RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)
            }
        }

        override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
            check(scope == this.scope)
            saves.incrementAndGet()
            synchronized(stateLock) { this.state = state }
            return true
        }

        fun currentState(): RemoteConfigSnapshotState = synchronized(stateLock) { state }
    }

    private class ControlledPreloader : RemoteConfigReadPreloader {
        private val completions = mutableListOf<(RemoteConfigReadPreloadResult) -> Unit>()
        override fun preload(
            scope: RemoteConfigSnapshotScope,
            prepareImplicitActivation: Boolean,
            completion: (RemoteConfigReadPreloadResult) -> Unit,
        ) {
            completions += completion
        }

        fun complete(index: Int, result: RemoteConfigReadPreloadResult) {
            completions[index](result)
        }
    }

    private class RecordingTelemetry : RemoteConfigReadTelemetry {
        val events = Collections.synchronizedList(mutableListOf<RemoteConfigReadGuardEvent>())
        override fun report(event: RemoteConfigReadGuardEvent) {
            events += event
        }
    }
}
