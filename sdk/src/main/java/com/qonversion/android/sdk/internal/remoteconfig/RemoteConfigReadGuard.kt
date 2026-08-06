package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal const val REMOTE_CONFIG_READ_BEFORE_ACTIVATE_MESSAGE =
    "Remote Config was read before activate(). Call activate() after SDK initialization and before reading current values."

internal enum class RemoteConfigReadBuildMode {
    Debug,
    Release,
}

internal fun interface RemoteConfigReadAssertion {
    fun fail(message: String)
}

internal enum class RemoteConfigReadGuardEvent {
    ReadBeforeActivate,
    ImplicitActivation,
    PreloadNotReady,
    PreloadFailed,
    PreloadCorrupt,
    ActivationPersistenceFailed,
}

internal fun interface RemoteConfigReadTelemetry {
    fun report(event: RemoteConfigReadGuardEvent)
}

internal enum class RemoteConfigReadPreloadStatus {
    Ready,
    Failed,
    Corrupt,
    PersistenceFailed,
}

internal data class RemoteConfigReadPreloadResult(
    val status: RemoteConfigReadPreloadStatus,
    val baseState: RemoteConfigSnapshotState? = null,
    val preparedActivationState: RemoteConfigSnapshotState? = null,
) {
    init {
        when (status) {
            RemoteConfigReadPreloadStatus.Ready -> require(baseState != null)
            RemoteConfigReadPreloadStatus.PersistenceFailed -> {
                require(baseState != null && preparedActivationState == null)
            }
            RemoteConfigReadPreloadStatus.Failed,
            RemoteConfigReadPreloadStatus.Corrupt,
            -> require(baseState == null && preparedActivationState == null)
        }
    }
}

internal interface RemoteConfigReadPreloader {
    fun preload(
        scope: RemoteConfigSnapshotScope,
        prepareImplicitActivation: Boolean,
        completion: (RemoteConfigReadPreloadResult) -> Unit,
    )
}

internal class PersistentRemoteConfigReadPreloader(
    private val store: RemoteConfigSnapshotStore,
    private val executor: Executor,
) : RemoteConfigReadPreloader {
    override fun preload(
        scope: RemoteConfigSnapshotScope,
        prepareImplicitActivation: Boolean,
        completion: (RemoteConfigReadPreloadResult) -> Unit,
    ) {
        val delivered = AtomicBoolean(false)
        fun deliver(result: RemoteConfigReadPreloadResult) {
            if (!delivered.compareAndSet(false, true)) return
            try {
                completion(result)
            } catch (_: Exception) {
                // The preload is terminal even if its internal lifecycle callback throws.
            }
        }
        try {
            executor.execute {
                deliver(load(scope, prepareImplicitActivation))
            }
        } catch (_: Exception) {
            deliver(RemoteConfigReadPreloadResult(RemoteConfigReadPreloadStatus.Failed))
        }
    }

    @Suppress("ReturnCount")
    private fun load(
        scope: RemoteConfigSnapshotScope,
        prepareImplicitActivation: Boolean,
    ): RemoteConfigReadPreloadResult {
        val loaded = try {
            store.load(scope)
        } catch (_: Exception) {
            RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Failed)
        }
        val baseState = when (loaded.status) {
            RemoteConfigSnapshotLoadStatus.Found -> requireNotNull(loaded.state)
            RemoteConfigSnapshotLoadStatus.Missing -> RemoteConfigSnapshotState()
            RemoteConfigSnapshotLoadStatus.Failed -> {
                return RemoteConfigReadPreloadResult(RemoteConfigReadPreloadStatus.Failed)
            }
            RemoteConfigSnapshotLoadStatus.Corrupt -> {
                return RemoteConfigReadPreloadResult(RemoteConfigReadPreloadStatus.Corrupt)
            }
        }
        if (!prepareImplicitActivation) {
            return RemoteConfigReadPreloadResult(
                status = RemoteConfigReadPreloadStatus.Ready,
                baseState = baseState,
            )
        }
        val preparedState = baseState.preparedActivationState()
        return RemoteConfigReadPreloadResult(
            status = RemoteConfigReadPreloadStatus.Ready,
            baseState = baseState,
            preparedActivationState = preparedState,
        )
    }
}

@Suppress("ReturnCount")
internal fun RemoteConfigSnapshotState.preparedActivationState(): RemoteConfigSnapshotState {
    val nextCandidate = candidate
    if (nextCandidate == null) return if (didActivate) this else copy(didActivate = true)
    if (didActivate && active?.admissionToken == nextCandidate.admissionToken) return this
    return RemoteConfigSnapshotState(
        candidate = nextCandidate,
        active = nextCandidate,
        previous = active,
        didActivate = true,
        latestAdmissionToken = latestAdmissionToken,
    )
}

internal class RemoteConfigReadGuard(
    private val core: RemoteConfigSnapshotCore,
    private val preloader: RemoteConfigReadPreloader,
    private val buildMode: RemoteConfigReadBuildMode,
    private val assertion: RemoteConfigReadAssertion,
    private val telemetry: RemoteConfigReadTelemetry,
) {
    private val lock = Any()
    private var preloadToken: RemoteConfigScopePreloadToken? = null
    private var preloadResult: RemoteConfigReadPreloadResult? = null
    private var firstReadHandled = false
    private var didConsumeImplicitActivation = false
    private var firstReadCommitBarrier: CountDownLatch? = null
    private var firstReadCommitOwner: Thread? = null
    private val reportedEvents = mutableSetOf<RemoteConfigReadGuardEvent>()

    fun transitionScopeBeforeSdkReady(
        scope: RemoteConfigSnapshotScope?,
        onReady: () -> Unit = {},
    ) {
        val prepareImplicitActivation = synchronized(lock) {
            buildMode == RemoteConfigReadBuildMode.Release && !didConsumeImplicitActivation
        }
        val token = core.beginScopePreload(
            scope = scope,
            armFirstReadActivation = prepareImplicitActivation,
        ) { boundToken ->
            synchronized(lock) {
                firstReadHandled = false
                preloadResult = null
                reportedEvents.clear()
                preloadToken = boundToken
            }
        }
        if (scope == null || token == null) {
            safelyInvoke(onReady)
            return
        }
        preloader.preload(
            scope = scope,
            prepareImplicitActivation = prepareImplicitActivation,
        ) { result ->
            completePreload(token, result, onReady)
        }
    }

    fun currentSnapshot(): RemoteConfigSnapshot {
        val events = mutableListOf<RemoteConfigReadGuardEvent>()
        var decision = FirstReadDecision()
        var waitForCommit: CountDownLatch?
        do {
            waitForCommit = null
            synchronized(lock) {
                val currentBarrier = firstReadCommitBarrier
                if (currentBarrier != null && firstReadCommitOwner !== Thread.currentThread()) {
                    waitForCommit = currentBarrier
                } else {
                    decision = claimFirstReadLocked(events)
                }
            }
            waitForCommit?.awaitUninterruptibly()
        } while (waitForCommit != null)
        val claimedToken = decision.claimedToken
        if (claimedToken != null) {
            val barrier = requireNotNull(decision.claimedBarrier)
            val transition = try {
                core.commitPrepersistedActivationWithoutDelivery(claimedToken)
            } finally {
                completeFirstReadCommit(barrier)
            }
            core.deliverPendingUpdates()
            if (transition.status == RemoteConfigSnapshotTransitionStatus.Activated) {
                events += RemoteConfigReadGuardEvent.ImplicitActivation
            }
        }
        val snapshot = core.currentSnapshot()
        report(events)
        decision.assertionMessage?.let { message -> assertion.fail(message) }
        return snapshot
    }

    private data class FirstReadDecision(
        val assertionMessage: String? = null,
        val claimedToken: RemoteConfigScopePreloadToken? = null,
        val claimedBarrier: CountDownLatch? = null,
    )

    private fun claimFirstReadLocked(
        events: MutableList<RemoteConfigReadGuardEvent>,
    ): FirstReadDecision = if (firstReadHandled) {
        FirstReadDecision()
    } else {
        firstReadHandled = true
        claimEvent(RemoteConfigReadGuardEvent.ReadBeforeActivate, events)
        when {
            buildMode == RemoteConfigReadBuildMode.Debug -> {
                FirstReadDecision(assertionMessage = REMOTE_CONFIG_READ_BEFORE_ACTIVATE_MESSAGE)
            }
            didConsumeImplicitActivation -> FirstReadDecision()
            else -> claimImplicitActivationLocked(events)
        }
    }

    private fun claimImplicitActivationLocked(
        events: MutableList<RemoteConfigReadGuardEvent>,
    ): FirstReadDecision {
        val token = preloadToken
        return when (core.claimImplicitActivationOpportunity(token)) {
            RemoteConfigImplicitActivationClaimStatus.Stale -> FirstReadDecision()
            RemoteConfigImplicitActivationClaimStatus.AlreadyConsumed -> {
                didConsumeImplicitActivation = true
                FirstReadDecision()
            }
            RemoteConfigImplicitActivationClaimStatus.Claimed -> {
                didConsumeImplicitActivation = true
                if (preloadResult == null) {
                    claimEvent(RemoteConfigReadGuardEvent.PreloadNotReady, events)
                }
                val barrier = CountDownLatch(1)
                firstReadCommitBarrier = barrier
                firstReadCommitOwner = Thread.currentThread()
                FirstReadDecision(
                    claimedToken = requireNotNull(token),
                    claimedBarrier = barrier,
                )
            }
        }
    }

    fun activate(): RemoteConfigSnapshotTransitionResult {
        val token = synchronized(lock) {
            firstReadHandled = true
            preloadToken
        }
        when (core.claimImplicitActivationOpportunity(token)) {
            RemoteConfigImplicitActivationClaimStatus.Stale -> Unit
            RemoteConfigImplicitActivationClaimStatus.AlreadyConsumed,
            RemoteConfigImplicitActivationClaimStatus.Claimed,
            -> synchronized(lock) { didConsumeImplicitActivation = true }
        }
        if (token != null) {
            val prepared = core.commitPrepersistedActivation(token)
            if (prepared.status != RemoteConfigSnapshotTransitionStatus.Ignored) return prepared
        }
        return core.activateForPreloadToken(token)
    }

    private fun completePreload(
        token: RemoteConfigScopePreloadToken,
        result: RemoteConfigReadPreloadResult,
        onReady: () -> Unit,
    ) {
        val events = mutableListOf<RemoteConfigReadGuardEvent>()
        val isCurrent = synchronized(lock) {
            if (preloadToken !== token) return@synchronized false
            if (!firstReadHandled) {
                var effectiveResult = result
                when (result.status) {
                    RemoteConfigReadPreloadStatus.Ready,
                    RemoteConfigReadPreloadStatus.PersistenceFailed,
                    -> {
                        val baseState = requireNotNull(result.baseState)
                        when (
                            core.installPreloadedScope(
                                token,
                                baseState,
                                result.preparedActivationState,
                            )
                        ) {
                            RemoteConfigScopePreloadInstallStatus.Ignored -> return@synchronized false
                            RemoteConfigScopePreloadInstallStatus.PersistenceFailed -> {
                                effectiveResult = RemoteConfigReadPreloadResult(
                                    status = RemoteConfigReadPreloadStatus.PersistenceFailed,
                                    baseState = baseState,
                                )
                            }
                            RemoteConfigScopePreloadInstallStatus.Superseded -> Unit
                            RemoteConfigScopePreloadInstallStatus.Installed -> Unit
                        }
                    }
                    RemoteConfigReadPreloadStatus.Failed,
                    RemoteConfigReadPreloadStatus.Corrupt,
                    -> Unit
                }
                preloadResult = effectiveResult
                when (effectiveResult.status) {
                    RemoteConfigReadPreloadStatus.Failed -> {
                        claimEvent(RemoteConfigReadGuardEvent.PreloadFailed, events)
                    }
                    RemoteConfigReadPreloadStatus.Corrupt -> {
                        claimEvent(RemoteConfigReadGuardEvent.PreloadCorrupt, events)
                    }
                    RemoteConfigReadPreloadStatus.PersistenceFailed -> {
                        claimEvent(RemoteConfigReadGuardEvent.ActivationPersistenceFailed, events)
                    }
                    RemoteConfigReadPreloadStatus.Ready -> Unit
                }
            }
            true
        }
        if (!isCurrent) return
        report(events)
        safelyInvoke(onReady)
    }

    private fun claimEvent(
        event: RemoteConfigReadGuardEvent,
        claimed: MutableList<RemoteConfigReadGuardEvent>,
    ) {
        if (reportedEvents.add(event)) claimed += event
    }

    private fun report(events: List<RemoteConfigReadGuardEvent>) {
        events.forEach { event ->
            try {
                telemetry.report(event)
            } catch (_: Exception) {
                // Telemetry can never affect serving state.
            }
        }
    }

    private fun safelyInvoke(callback: () -> Unit) {
        try {
            callback()
        } catch (_: Exception) {
            // SDK readiness is terminal even if an internal lifecycle callback throws.
        }
    }

    private fun completeFirstReadCommit(barrier: CountDownLatch) {
        synchronized(lock) {
            if (firstReadCommitBarrier === barrier) {
                firstReadCommitBarrier = null
                firstReadCommitOwner = null
            }
        }
        barrier.countDown()
    }

    private fun CountDownLatch.awaitUninterruptibly() {
        var interrupted = false
        while (true) {
            try {
                await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
