package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import java.util.ArrayDeque

internal enum class RemoteConfigSnapshotTransitionStatus {
    Accepted,
    Activated,
    Ignored,
    PersistenceFailed,
    Unchanged,
}

internal data class RemoteConfigSnapshotTransitionResult(
    val status: RemoteConfigSnapshotTransitionStatus,
    val changed: Boolean = false,
    val update: RemoteConfigSnapshotUpdate? = null,
)

internal class RemoteConfigSnapshotCore(
    private val store: RemoteConfigSnapshotStore,
    private val bundledRelease: RemoteConfigScopedBundledRelease?,
) {
    private val lock = Any()
    private val deliveryLock = Any()
    private var currentScope: RemoteConfigSnapshotScope? = null
    private var state = RemoteConfigSnapshotState()
    private var scopeLoadFailed = false
    private var scopeGeneration = 0L
    private var nextObserverToken = 0L
    private val observers = linkedMapOf<Long, (RemoteConfigSnapshotUpdate) -> Unit>()
    private val pendingDeliveries = ArrayDeque<TransitionDelivery>()
    private var isDrainingDeliveries = false

    fun setScope(scope: RemoteConfigSnapshotScope?) {
        synchronized(deliveryLock) {
            synchronized(lock) {
                if (currentScope == scope && !(scope != null && scopeLoadFailed)) return
                if (currentScope != scope) {
                    currentScope = scope
                    state = RemoteConfigSnapshotState()
                    scopeLoadFailed = false
                    scopeGeneration++
                }
                scope?.let(::loadScopeState)
            }
        }
    }

    fun currentSnapshot(): RemoteConfigSnapshot = synchronized(lock) {
        snapshotFor(state.active, state.previous)
    }

    fun lastFetchedSnapshot(): RemoteConfigSnapshot? = synchronized(lock) {
        state.candidate?.let { candidate ->
            val previous = if (candidate.isSameRelease(state.active)) state.previous else state.active
            snapshotFor(candidate, previous)
        }
    }

    fun addUpdateObserver(observer: (RemoteConfigSnapshotUpdate) -> Unit): Long = synchronized(lock) {
        val token = ++nextObserverToken
        observers[token] = observer
        token
    }

    fun removeUpdateObserver(token: Long) {
        synchronized(lock) { observers.remove(token) }
    }

    fun acceptCandidate(
        scope: RemoteConfigSnapshotScope,
        release: RemoteConfigSnapshotRelease,
    ): RemoteConfigSnapshotTransitionResult {
        val delivery = synchronized(lock) {
            if (scope != currentScope) return@synchronized TransitionDelivery.ignored()
            if (!ensureCurrentScopeLoaded()) return@synchronized TransitionDelivery.persistenceFailed()
            val latestNumber = maxOf(
                state.candidate?.releaseNumber ?: 0,
                state.active?.releaseNumber ?: 0,
            )
            if (release.releaseNumber <= latestNumber) return@synchronized TransitionDelivery.ignored()

            val oldSnapshot = snapshotFor(state.active, state.previous)
            val nextState = if (release.containsImmediateEntry) {
                RemoteConfigSnapshotState(
                    candidate = release,
                    active = release,
                    previous = state.active,
                    didActivate = true,
                )
            } else {
                state.copy(candidate = release)
            }
            if (!saveCurrentScope(nextState)) return@synchronized TransitionDelivery.persistenceFailed()
            state = nextState
            if (release.containsImmediateEntry) {
                val update = buildUpdate(oldSnapshot, snapshotFor(nextState.active, nextState.previous))
                TransitionDelivery(
                    result = RemoteConfigSnapshotTransitionResult(
                        status = RemoteConfigSnapshotTransitionStatus.Activated,
                        changed = update.changedKeys.isNotEmpty(),
                        update = update,
                    ),
                    update = update,
                    observers = observers.values.toList(),
                    scopeGeneration = scopeGeneration,
                ).also(::enqueueDeliveryLocked)
            } else {
                TransitionDelivery(
                    RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Accepted),
                )
            }
        }
        drainDeliveries()
        return delivery.result
    }

    fun activate(): RemoteConfigSnapshotTransitionResult {
        val delivery = synchronized(lock) {
            if (currentScope == null) return@synchronized TransitionDelivery.ignored()
            if (!ensureCurrentScopeLoaded()) return@synchronized TransitionDelivery.persistenceFailed()
            val candidate = state.candidate
            if (candidate == null) {
                if (state.didActivate) return@synchronized TransitionDelivery.unchanged()
                val nextState = state.copy(didActivate = true)
                if (!saveCurrentScope(nextState)) return@synchronized TransitionDelivery.persistenceFailed()
                val oldSnapshot = snapshotFor(state.active, state.previous)
                state = nextState
                val update = buildUpdate(oldSnapshot = null, newSnapshot = oldSnapshot)
                return@synchronized TransitionDelivery
                    .activated(update, observers.values.toList(), scopeGeneration)
                    .also(::enqueueDeliveryLocked)
            }
            if (state.didActivate && candidate.isSameRelease(state.active)) {
                return@synchronized TransitionDelivery.unchanged()
            }

            val oldSnapshot = snapshotFor(state.active, state.previous)
            val nextState = RemoteConfigSnapshotState(
                candidate = candidate,
                active = candidate,
                previous = state.active,
                didActivate = true,
            )
            if (!saveCurrentScope(nextState)) return@synchronized TransitionDelivery.persistenceFailed()
            state = nextState
            val update = buildUpdate(oldSnapshot, snapshotFor(nextState.active, nextState.previous))
            TransitionDelivery
                .activated(update, observers.values.toList(), scopeGeneration)
                .also(::enqueueDeliveryLocked)
        }
        drainDeliveries()
        return delivery.result
    }

    private fun ensureCurrentScopeLoaded(): Boolean {
        val scope = currentScope
        if (scope != null && scopeLoadFailed) loadScopeState(scope)
        return scope != null && !scopeLoadFailed
    }

    private fun loadScopeState(scope: RemoteConfigSnapshotScope) {
        val result = try {
            store.load(scope)
        } catch (_: Exception) {
            RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Failed)
        }
        when (result.status) {
            RemoteConfigSnapshotLoadStatus.Found -> {
                state = requireNotNull(result.state)
                scopeLoadFailed = false
            }
            RemoteConfigSnapshotLoadStatus.Missing -> {
                state = RemoteConfigSnapshotState()
                scopeLoadFailed = false
            }
            RemoteConfigSnapshotLoadStatus.Failed -> {
                scopeLoadFailed = true
            }
        }
    }

    private fun saveCurrentScope(nextState: RemoteConfigSnapshotState): Boolean {
        val scope = currentScope ?: return false
        return try {
            store.save(scope, nextState)
        } catch (_: Exception) {
            false
        }
    }

    private fun snapshotFor(
        primary: RemoteConfigSnapshotRelease?,
        previous: RemoteConfigSnapshotRelease?,
    ) = RemoteConfigSnapshot(primary, previous, bundledRelease?.releaseFor(currentScope))

    private fun buildUpdate(
        oldSnapshot: RemoteConfigSnapshot?,
        newSnapshot: RemoteConfigSnapshot,
    ): RemoteConfigSnapshotUpdate {
        val allKeys = oldSnapshot?.allKeys.orEmpty() + newSnapshot.allKeys
        val changedKeys = allKeys.filterTo(mutableSetOf()) { key ->
            val oldEntry = oldSnapshot?.effectiveEntry(key)
            val newEntry = newSnapshot.effectiveEntry(key)
            when {
                oldEntry == null -> newEntry != null
                else -> !oldEntry.contentEquals(newEntry)
            }
        }
        val metadata = changedKeys.mapNotNull { key ->
            newSnapshot.metadataForKey(key)?.let { key to it }
        }.toMap()
        return RemoteConfigSnapshotUpdate(newSnapshot, changedKeys, metadata)
    }

    private fun enqueueDeliveryLocked(delivery: TransitionDelivery) {
        if (delivery.update?.changedKeys?.isNotEmpty() == true) pendingDeliveries.addLast(delivery)
    }

    private fun drainDeliveries() {
        synchronized(deliveryLock) {
            if (isDrainingDeliveries) return
            isDrainingDeliveries = true
            try {
                while (true) {
                    val delivery = synchronized(lock) { pendingDeliveries.pollFirst() } ?: break
                    deliverIfCurrent(delivery)
                }
            } finally {
                isDrainingDeliveries = false
            }
        }
    }

    private fun deliverIfCurrent(delivery: TransitionDelivery) {
        val update = requireNotNull(delivery.update)
        val generation = requireNotNull(delivery.scopeGeneration)
        for (observer in delivery.observers) {
            val isCurrent = synchronized(lock) { generation == scopeGeneration }
            if (!isCurrent) break
            try {
                observer(update)
            } catch (_: Exception) {
                // A committed transition remains successful and other observers still run.
            }
        }
    }

    private fun RemoteConfigSnapshotRelease.isSameRelease(other: RemoteConfigSnapshotRelease?): Boolean =
        contentEquals(other)

    private data class TransitionDelivery(
        val result: RemoteConfigSnapshotTransitionResult,
        val update: RemoteConfigSnapshotUpdate? = null,
        val observers: List<(RemoteConfigSnapshotUpdate) -> Unit> = emptyList(),
        val scopeGeneration: Long? = null,
    ) {
        companion object {
            fun ignored() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Ignored),
            )

            fun persistenceFailed() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.PersistenceFailed),
            )

            fun unchanged() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Unchanged),
            )

            fun activated(
                update: RemoteConfigSnapshotUpdate,
                observers: List<(RemoteConfigSnapshotUpdate) -> Unit>,
                scopeGeneration: Long,
            ) = TransitionDelivery(
                result = RemoteConfigSnapshotTransitionResult(
                    status = RemoteConfigSnapshotTransitionStatus.Activated,
                    changed = update.changedKeys.isNotEmpty(),
                    update = update,
                ),
                update = update,
                observers = observers,
                scopeGeneration = scopeGeneration,
            )
        }
    }
}
