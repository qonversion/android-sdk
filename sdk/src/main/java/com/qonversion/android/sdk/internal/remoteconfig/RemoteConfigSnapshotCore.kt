package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadResult
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotLoadStatus
import com.qonversion.android.sdk.internal.storage.RemoteConfigSnapshotStore
import java.util.ArrayDeque
import java.util.UUID

internal enum class RemoteConfigSnapshotTransitionStatus {
    Accepted,
    Activated,
    Ignored,
    PersistenceFailed,
    Rejected,
    Unchanged,
}

internal data class RemoteConfigSnapshotTransitionResult(
    val status: RemoteConfigSnapshotTransitionStatus,
    val changed: Boolean = false,
    val update: RemoteConfigSnapshotUpdate? = null,
)

internal class RemoteConfigSnapshotAdmissionToken private constructor(
    private val ownerNonce: UUID,
    private val admission: BoundRemoteConfigSnapshotAdmission,
) {
    internal fun resolve(ownerNonce: UUID): BoundRemoteConfigSnapshotAdmission? =
        admission.takeIf { this.ownerNonce == ownerNonce }

    internal companion object {
        fun issue(
            ownerNonce: UUID,
            ordinal: Long,
            scope: RemoteConfigSnapshotScope,
            scopeGeneration: Long,
            expectation: RemoteConfigSnapshotEnvelopeExpectation,
        ) = RemoteConfigSnapshotAdmissionToken(
            ownerNonce = ownerNonce,
            admission = BoundRemoteConfigSnapshotAdmission(
                ordinal = ordinal,
                scope = scope,
                scopeGeneration = scopeGeneration,
                expectation = expectation,
            ),
        )
    }
}

internal data class BoundRemoteConfigSnapshotAdmission(
    val ordinal: Long,
    val scope: RemoteConfigSnapshotScope,
    val scopeGeneration: Long,
    val expectation: RemoteConfigSnapshotEnvelopeExpectation,
)

internal class RemoteConfigSnapshotCore(
    private val store: RemoteConfigSnapshotStore,
    private val bundledRelease: RemoteConfigScopedBundledRelease?,
    private val envelopeParser: RemoteConfigSnapshotEnvelopeDecoder = RemoteConfigSnapshotEnvelopeParser(),
) {
    private val lock = Any()
    private val deliveryLock = Any()
    private val admissionOwnerNonce = UUID.randomUUID()
    private var currentScope: RemoteConfigSnapshotScope? = null
    private var state = RemoteConfigSnapshotState()
    private var scopeLoadFailed = false
    private var scopeGeneration = 0L
    private var nextAdmissionToken = 0L
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
                    nextAdmissionToken = 0L
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

    fun beginAdmission(
        scope: RemoteConfigSnapshotScope,
        expectation: RemoteConfigSnapshotEnvelopeExpectation,
    ): RemoteConfigSnapshotAdmissionToken? =
        synchronized(lock) {
            if (expectation.environmentUid != scope.environment) return@synchronized null
            val admission = issueAdmissionLocked(scope) ?: return@synchronized null
            RemoteConfigSnapshotAdmissionToken.issue(
                ownerNonce = admissionOwnerNonce,
                ordinal = admission.ordinal,
                scope = scope,
                scopeGeneration = admission.scopeGeneration,
                expectation = expectation,
            )
        }

    private fun issueAdmissionLocked(scope: RemoteConfigSnapshotScope): IssuedAdmission? {
        if (scope != currentScope || !ensureCurrentScopeLoaded() || nextAdmissionToken == Long.MAX_VALUE) {
            return null
        }
        return IssuedAdmission(
            ordinal = ++nextAdmissionToken,
            scopeGeneration = scopeGeneration,
        )
    }

    private data class IssuedAdmission(
        val ordinal: Long,
        val scopeGeneration: Long,
    )

    private fun issueAdmission(scope: RemoteConfigSnapshotScope): IssuedAdmission? = synchronized(lock) {
        issueAdmissionLocked(scope)
    }

    private fun failedDirectAdmission(scope: RemoteConfigSnapshotScope) = synchronized(lock) {
        RemoteConfigSnapshotTransitionResult(
            if (scope == currentScope) {
                RemoteConfigSnapshotTransitionStatus.PersistenceFailed
            } else {
                RemoteConfigSnapshotTransitionStatus.Ignored
            },
        )
    }

    fun acceptCandidate(
        scope: RemoteConfigSnapshotScope,
        release: RemoteConfigSnapshotRelease,
    ): RemoteConfigSnapshotTransitionResult {
        val admission = issueAdmission(scope) ?: return failedDirectAdmission(scope)
        return acceptCandidate(
            scope = scope,
            release = release,
            admissionOrdinal = admission.ordinal,
            admissionScopeGeneration = admission.scopeGeneration,
            authoritativeComplete = false,
        )
    }

    @Suppress("ReturnCount")
    fun admitCandidate(
        admissionToken: RemoteConfigSnapshotAdmissionToken,
        body: ByteArray,
        etag: String,
    ): RemoteConfigSnapshotTransitionResult {
        val admission = admissionToken.resolve(admissionOwnerNonce)
            ?: return RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Rejected)
        val tokenIsCurrent = synchronized(lock) {
            admission.scope == currentScope &&
                admission.scopeGeneration == scopeGeneration &&
                admission.ordinal == nextAdmissionToken &&
                admission.ordinal > state.latestAdmissionToken
        }
        if (!tokenIsCurrent) {
            return RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Rejected)
        }
        val envelope = envelopeParser.parse(body, etag, admission.expectation)
            ?: return RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Rejected)
        return acceptCandidate(
            scope = admission.scope,
            release = envelope.release,
            admissionOrdinal = admission.ordinal,
            admissionScopeGeneration = admission.scopeGeneration,
            authoritativeComplete = true,
        )
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun acceptCandidate(
        scope: RemoteConfigSnapshotScope,
        release: RemoteConfigSnapshotRelease,
        admissionOrdinal: Long,
        admissionScopeGeneration: Long,
        authoritativeComplete: Boolean,
    ): RemoteConfigSnapshotTransitionResult {
        val delivery = synchronized(lock) {
            if (scope != currentScope || admissionScopeGeneration != scopeGeneration) {
                return@synchronized if (authoritativeComplete) {
                    TransitionDelivery.rejected()
                } else {
                    TransitionDelivery.ignored()
                }
            }
            if (!ensureCurrentScopeLoaded()) return@synchronized TransitionDelivery.persistenceFailed()
            if (admissionOrdinal != nextAdmissionToken || admissionOrdinal <= state.latestAdmissionToken) {
                return@synchronized if (authoritativeComplete) {
                    TransitionDelivery.rejected()
                } else {
                    TransitionDelivery.ignored()
                }
            }
            val releaseNumberFloor = maxOf(
                state.candidate?.releaseNumber ?: 0,
                state.active?.releaseNumber ?: 0,
            )
            if (release.releaseNumber < releaseNumberFloor) {
                return@synchronized if (authoritativeComplete) {
                    TransitionDelivery.rejected()
                } else {
                    TransitionDelivery.ignored()
                }
            }
            val tokenizedRelease = release.withAdmissionToken(admissionOrdinal)
            val admittedRelease = if (authoritativeComplete) {
                tokenizedRelease.withMissingActiveKeysTombstoned(state.active)
                    ?: return@synchronized TransitionDelivery.rejected()
            } else {
                tokenizedRelease
            }

            val oldSnapshot = snapshotFor(state.active, state.previous)
            val nextState = if (admittedRelease.containsImmediateEntry) {
                RemoteConfigSnapshotState(
                    candidate = admittedRelease,
                    active = admittedRelease,
                    previous = state.active,
                    didActivate = true,
                    latestAdmissionToken = admissionOrdinal,
                )
            } else {
                state.copy(candidate = admittedRelease, latestAdmissionToken = admissionOrdinal)
            }
            if (!saveCurrentScope(nextState)) return@synchronized TransitionDelivery.persistenceFailed()
            state = nextState
            if (admittedRelease.containsImmediateEntry) {
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
                    admissionOrdinal = admissionOrdinal,
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
                latestAdmissionToken = state.latestAdmissionToken,
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
                nextAdmissionToken = state.latestAdmissionToken
                scopeLoadFailed = false
            }
            RemoteConfigSnapshotLoadStatus.Missing -> {
                state = RemoteConfigSnapshotState()
                nextAdmissionToken = 0L
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
                    val delivery = synchronized(lock) { pollCurrentDeliveryLocked() } ?: break
                    deliverIfCurrent(delivery)
                }
            } finally {
                isDrainingDeliveries = false
            }
        }
    }

    private fun pollCurrentDeliveryLocked(): TransitionDelivery? {
        while (pendingDeliveries.isNotEmpty()) {
            val delivery = pendingDeliveries.removeFirst()
            val generationIsCurrent = delivery.scopeGeneration == scopeGeneration
            val admissionIsCurrent = delivery.admissionOrdinal?.let { it == nextAdmissionToken } ?: true
            if (generationIsCurrent && admissionIsCurrent) return delivery
        }
        return null
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
        other != null && admissionToken == other.admissionToken

    private data class TransitionDelivery(
        val result: RemoteConfigSnapshotTransitionResult,
        val update: RemoteConfigSnapshotUpdate? = null,
        val observers: List<(RemoteConfigSnapshotUpdate) -> Unit> = emptyList(),
        val scopeGeneration: Long? = null,
        val admissionOrdinal: Long? = null,
    ) {
        companion object {
            fun ignored() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Ignored),
            )

            fun persistenceFailed() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.PersistenceFailed),
            )

            fun rejected() = TransitionDelivery(
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Rejected),
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

private fun RemoteConfigSnapshotRelease.withMissingActiveKeysTombstoned(
    active: RemoteConfigSnapshotRelease?,
): RemoteConfigSnapshotRelease? {
    val missingActiveKeys = active?.entries?.values.orEmpty()
        .asSequence()
        .filterNot(RemoteConfigSnapshotEntry::isTombstone)
        .map(RemoteConfigSnapshotEntry::key)
        .filterNot(entries::containsKey)
        .toList()
    if (missingActiveKeys.isEmpty()) return this
    return try {
        RemoteConfigSnapshotRelease(
            releaseUid = releaseUid,
            releaseNumber = releaseNumber,
            manifestContentHash = manifestContentHash,
            entries = entries.values + missingActiveKeys.map(RemoteConfigSnapshotEntry::tombstone),
            canonicalBody = canonicalBodyBytes,
            strongETag = strongETag,
            contextFingerprint = contextFingerprint,
            admissionToken = admissionToken,
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
