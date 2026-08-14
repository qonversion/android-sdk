package com.qonversion.android.sdk.internal.remoteconfig

import java.util.ArrayDeque

internal enum class RemoteConfigFetchForceReason {
    Build,
    Identify,
    Logout,
}

internal data class RemoteConfigFetchPolicy(
    val minimumFetchIntervalMillis: Long,
    val timeoutMillis: Long? = null,
    val initialBackoffMillis: Long = 1_000,
    val maximumBackoffMillis: Long = 60_000,
) {
    init {
        require(minimumFetchIntervalMillis >= 0)
        require(timeoutMillis == null || timeoutMillis > 0)
        require(initialBackoffMillis > 0)
        require(maximumBackoffMillis >= initialBackoffMillis)
    }
}

internal data class RemoteConfigFetchPolicyState(
    val lastSuccessfulFetchAtMillis: Long = 0,
    val consecutiveRetryableFailures: Int = 0,
    val nextAllowedFetchAtMillis: Long = 0,
)

internal data class RemoteConfigFetchPolicyScope(
    val projectKey: String,
    val environment: String,
) {
    init {
        require(projectKey.isNotEmpty())
        require(environment.isNotEmpty())
    }

    companion object {
        fun from(scope: RemoteConfigSnapshotScope) = RemoteConfigFetchPolicyScope(
            projectKey = scope.projectKey,
            environment = scope.environment,
        )
    }
}

internal interface RemoteConfigFetchPolicyStore {
    fun load(scope: RemoteConfigFetchPolicyScope): RemoteConfigFetchPolicyState?
    fun save(scope: RemoteConfigFetchPolicyScope, state: RemoteConfigFetchPolicyState): Boolean
}

internal fun interface RemoteConfigFetchClock {
    fun nowMillis(): Long
}

internal fun interface RemoteConfigFetchRandom {
    fun nextDouble(): Double
}

internal fun interface RemoteConfigFetchScheduledTask {
    fun cancel()
}

internal fun interface RemoteConfigFetchScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): RemoteConfigFetchScheduledTask
}

internal data class RemoteConfigFetchRequest(
    val ifNoneMatch: String? = null,
)

internal sealed class RemoteConfigFetchResponse {
    /**
     * [projectId] is the project the transport's session was minted for — the SDK's only source for
     * it — and is what the envelope's own project id is admitted against.
     */
    data class Success(
        val body: ByteArray,
        val etag: String,
        val projectId: Long,
    ) : RemoteConfigFetchResponse()

    data class NotModified(val etag: String? = null) : RemoteConfigFetchResponse()
    data class Failure(
        val statusCode: Int? = null,
        val retryAfterMillis: Long? = null,
    ) : RemoteConfigFetchResponse()

    /**
     * The transport was answered for a different project than the one this installation
     * established. Permanent until the gateway is fixed, and the refusal costs a bootstrap round
     * trip every time, so it feeds the failure backoff: forced fetches bypass the minimum interval
     * but NOT the backoff gate, which is what keeps an identify/logout loop from turning a
     * misrouted gateway into a request storm.
     */
    data object ProjectMismatch : RemoteConfigFetchResponse()
}

internal fun interface RemoteConfigFetchTransport {
    fun fetch(request: RemoteConfigFetchRequest, completion: (RemoteConfigFetchResponse) -> Unit)
}

internal sealed class RemoteConfigFetchResult {
    data class Fetched(val transition: RemoteConfigSnapshotTransitionResult) : RemoteConfigFetchResult()
    data object NotModified : RemoteConfigFetchResult()
    data class Failed(val statusCode: Int?) : RemoteConfigFetchResult()
    data class MinimumInterval(val nextAllowedAtMillis: Long) : RemoteConfigFetchResult()
    data class Backoff(val nextAllowedAtMillis: Long) : RemoteConfigFetchResult()
    data class TimedOut(val snapshot: RemoteConfigSnapshot) : RemoteConfigFetchResult()
    data class PolicyPersistenceFailed(val result: RemoteConfigFetchResult) : RemoteConfigFetchResult()
    data object InvalidNotModified : RemoteConfigFetchResult()
    data object Superseded : RemoteConfigFetchResult()
    data object ProjectMismatch : RemoteConfigFetchResult()
}

internal class RemoteConfigFetchCoordinator(
    private val core: RemoteConfigSnapshotCore,
    private val transport: RemoteConfigFetchTransport,
    private val policyStore: RemoteConfigFetchPolicyStore,
    private val clock: RemoteConfigFetchClock,
    private val random: RemoteConfigFetchRandom,
    private val scheduler: RemoteConfigFetchScheduler,
    private val policy: RemoteConfigFetchPolicy,
    private val policyPersistenceFailureObserver: (RemoteConfigFetchPolicyScope) -> Unit = {},
) {
    private val lock = Any()
    private val operationLock = Any()
    private val deliveryLock = Any()
    private val pendingDeliveries = ArrayDeque<PendingDelivery>()
    private var isDrainingDeliveries = false
    private var boundScope: RemoteConfigSnapshotScope? = null
    private var operationGeneration = 0L
    private var inFlight: InFlight? = null
    private var policyState = RemoteConfigFetchPolicyState()

    fun transitionTo(nextScope: RemoteConfigSnapshotScope?) {
        val persistenceFailure = synchronized(operationLock) {
            synchronized(lock) {
                operationGeneration = nextGeneration(operationGeneration)
                boundScope = nextScope
                core.setScope(nextScope)
                val loaded = nextScope?.let {
                    loadPolicyState(RemoteConfigFetchPolicyScope.from(it))
                } ?: LoadedPolicyState(RemoteConfigFetchPolicyState())
                policyState = loaded.state
                val superseded = inFlight?.let { operation ->
                    claimWaitersLocked(
                        operation = operation,
                        result = RemoteConfigFetchResult.Superseded,
                    )
                }.orEmpty()
                inFlight = null
                enqueueDeliveriesLocked(superseded)
                convertQueuedDeliveriesToSupersededLocked(operationGeneration)
                loaded.persistenceFailure
            }
        }
        persistenceFailure?.let(::observePolicyPersistenceFailure)
        drainDeliveries()
    }

    fun fetch(
        forceReason: RemoteConfigFetchForceReason? = null,
        callback: (RemoteConfigFetchResult) -> Unit,
    ) {
        val decision = synchronized(lock) { decideFetchLocked(forceReason, callback) }
        decision.immediateResult?.let { result ->
            enqueueImmediateResult(decision.generation, callback, result)
            return
        }
        val operation = requireNotNull(decision.operation)
        scheduleTimeout(operation, requireNotNull(decision.waiter))
        if (decision.shouldStart) startAttempt(operation)
    }

    @Suppress("ReturnCount")
    private fun decideFetchLocked(
        forceReason: RemoteConfigFetchForceReason?,
        callback: (RemoteConfigFetchResult) -> Unit,
    ): FetchDecision {
        inFlight?.takeIf { it.waiters.any { waiter -> !waiter.terminalClaimed } }?.let { current ->
            return FetchDecision.joined(
                generation = operationGeneration,
                operation = current,
                waiter = FetchWaiter(callback).also(current.waiters::add),
            )
        }
        // A request with no live waiters continues in the transport, but a new caller owns a new
        // admission token. This fences the zombie response without relying on HTTP cancellation.
        inFlight = null
        val currentScope = boundScope
            ?: return FetchDecision.immediate(operationGeneration, RemoteConfigFetchResult.Superseded)
        fetchGateLocked(forceReason, nowMillis())?.let { gate ->
            return FetchDecision.immediate(operationGeneration, gate)
        }
        val admission = core.beginAdmission(currentScope)
            ?: return FetchDecision.immediate(
                operationGeneration,
                RemoteConfigFetchResult.Failed(statusCode = null),
            )
        val operation = InFlight(
            generation = operationGeneration,
            scope = currentScope,
            admission = admission,
            waiters = mutableListOf(),
            conditionalValidator = core.conditionalRequestValidator(),
        )
        val waiter = FetchWaiter(callback).also(operation.waiters::add)
        inFlight = operation
        return FetchDecision.started(operationGeneration, operation, waiter)
    }

    private fun fetchGateLocked(
        forceReason: RemoteConfigFetchForceReason?,
        now: Long,
    ): RemoteConfigFetchResult? = when {
        now < policyState.nextAllowedFetchAtMillis ->
            RemoteConfigFetchResult.Backoff(policyState.nextAllowedFetchAtMillis)
        shouldApplyMinimumInterval(forceReason, now) -> RemoteConfigFetchResult.MinimumInterval(
            saturatingAdd(
                policyState.lastSuccessfulFetchAtMillis,
                policy.minimumFetchIntervalMillis,
            ),
        )
        else -> null
    }

    private fun enqueueImmediateResult(
        generation: Long,
        callback: (RemoteConfigFetchResult) -> Unit,
        result: RemoteConfigFetchResult,
    ) {
        synchronized(operationLock) {
            synchronized(lock) {
                val terminal = result.takeIf { generation == operationGeneration }
                    ?: RemoteConfigFetchResult.Superseded
                val waiter = FetchWaiter(callback).also { it.terminalClaimed = true }
                enqueueDeliveriesLocked(listOf(PendingDelivery(generation, waiter, terminal)))
            }
        }
        drainDeliveries()
    }

    private fun startAttempt(operation: InFlight) {
        val attemptAndRequest = synchronized(lock) {
            if (inFlight !== operation || operation.generation != operationGeneration) return
            ++operation.attemptOrdinal to RemoteConfigFetchRequest(
                ifNoneMatch = operation.conditionalValidator?.etag,
            )
        }
        val (attempt, request) = attemptAndRequest
        try {
            transport.fetch(request) { response -> complete(operation, attempt, response) }
        } catch (_: Throwable) {
            complete(operation, attempt, RemoteConfigFetchResponse.Failure())
        }
    }

    @Suppress("ComplexMethod", "ReturnCount")
    private fun complete(
        operation: InFlight,
        attemptOrdinal: Long,
        response: RemoteConfigFetchResponse,
    ) {
        var retry = false
        var persistenceFailure: RemoteConfigFetchPolicyScope? = null
        synchronized(operationLock) {
            val notModifiedDisposition = if (response is RemoteConfigFetchResponse.NotModified) {
                notModifiedDisposition(operation, attemptOrdinal, response)
            } else {
                NotModifiedDisposition.NotApplicable
            }
            if (notModifiedDisposition == NotModifiedDisposition.Ignore) return
            if (notModifiedDisposition == NotModifiedDisposition.Retry) {
                retry = true
                return@synchronized
            }
            val isCurrent = synchronized(lock) { operation.isCurrentLocked(attemptOrdinal) }
            if (!isCurrent) return

            val outcome = responseOutcome(operation, response, notModifiedDisposition)
            synchronized(lock) {
                if (!operation.isCurrentLocked(attemptOrdinal)) return
                outcome.nextPolicyState?.let { policyState = it }
                val persisted = outcome.nextPolicyState?.let { state ->
                    savePolicyState(operation.policyScope, state)
                } ?: true
                val terminalResult = if (persisted) {
                    outcome.result
                } else {
                    persistenceFailure = operation.policyScope
                    RemoteConfigFetchResult.PolicyPersistenceFailed(outcome.result)
                }
                inFlight = null
                enqueueDeliveriesLocked(claimWaitersLocked(operation, terminalResult))
            }
        }
        persistenceFailure?.let(::observePolicyPersistenceFailure)
        if (retry) startAttempt(operation) else drainDeliveries()
    }

    private fun notModifiedDisposition(
        operation: InFlight,
        attemptOrdinal: Long,
        response: RemoteConfigFetchResponse.NotModified,
    ): NotModifiedDisposition = synchronized(lock) {
        if (!operation.isCurrentLocked(attemptOrdinal)) {
            return@synchronized NotModifiedDisposition.Ignore
        }
        val validator = operation.conditionalValidator
        val responseMatchesRequest = response.etag == null || response.etag == validator?.etag
        if (validator != null && responseMatchesRequest &&
            core.isConditionalRequestValidatorCurrent(validator)
        ) {
            return@synchronized NotModifiedDisposition.Accept
        }
        if (operation.didRetryWithoutETag) return@synchronized NotModifiedDisposition.Reject
        val refreshedAdmission = core.beginAdmission(operation.scope)
            ?: return@synchronized NotModifiedDisposition.Reject
        operation.didRetryWithoutETag = true
        operation.conditionalValidator = null
        operation.admission = refreshedAdmission
        NotModifiedDisposition.Retry
    }

    private fun responseOutcome(
        operation: InFlight,
        response: RemoteConfigFetchResponse,
        notModifiedDisposition: NotModifiedDisposition,
    ): ResponseOutcome = when (response) {
        is RemoteConfigFetchResponse.Success -> {
            val transition = core.admitCandidate(
                admissionToken = operation.admission,
                body = response.body,
                etag = response.etag,
                projectId = response.projectId,
            )
            val succeeded = transition.status == RemoteConfigSnapshotTransitionStatus.Accepted ||
                transition.status == RemoteConfigSnapshotTransitionStatus.Activated
            ResponseOutcome(
                result = RemoteConfigFetchResult.Fetched(transition),
                nextPolicyState = RemoteConfigFetchPolicyState(lastSuccessfulFetchAtMillis = nowMillis())
                    .takeIf { succeeded },
            )
        }
        is RemoteConfigFetchResponse.NotModified -> if (notModifiedDisposition == NotModifiedDisposition.Accept) {
            ResponseOutcome(
                result = RemoteConfigFetchResult.NotModified,
                nextPolicyState = RemoteConfigFetchPolicyState(lastSuccessfulFetchAtMillis = nowMillis()),
            )
        } else {
            ResponseOutcome(RemoteConfigFetchResult.InvalidNotModified)
        }
        is RemoteConfigFetchResponse.Failure -> ResponseOutcome(
            result = RemoteConfigFetchResult.Failed(response.statusCode),
            nextPolicyState = retryableFailureState(response).takeIf { response.isRetryable() },
        )
        RemoteConfigFetchResponse.ProjectMismatch -> ResponseOutcome(
            result = RemoteConfigFetchResult.ProjectMismatch,
            nextPolicyState = retryableFailureState(RemoteConfigFetchResponse.Failure()),
        )
    }

    private fun scheduleTimeout(operation: InFlight, waiter: FetchWaiter) {
        val timeoutMillis = policy.timeoutMillis ?: return
        val task = try {
            scheduler.schedule(timeoutMillis) { timeout(operation, waiter) }
        } catch (_: Throwable) {
            return
        }
        val retained = synchronized(lock) {
            if (isLiveWaiterLocked(operation, waiter)) {
                waiter.timeoutTask = task
                true
            } else {
                false
            }
        }
        if (!retained) task.cancelSafely()
    }

    private fun timeout(operation: InFlight, waiter: FetchWaiter) {
        synchronized(operationLock) {
            synchronized(lock) {
                if (!isLiveWaiterLocked(operation, waiter) || !operation.waiters.remove(waiter)) {
                    return
                }
                val snapshot = core.currentSnapshot()
                waiter.terminalClaimed = true
                enqueueDeliveriesLocked(
                    listOf(
                        PendingDelivery(
                            generation = operation.generation,
                            waiter = waiter,
                            result = RemoteConfigFetchResult.TimedOut(snapshot),
                        ),
                    ),
                )
            }
        }
        drainDeliveries()
    }

    private fun claimWaitersLocked(
        operation: InFlight,
        result: RemoteConfigFetchResult,
    ): List<PendingDelivery> = operation.waiters.mapNotNull { waiter ->
        if (waiter.terminalClaimed) {
            null
        } else {
            waiter.terminalClaimed = true
            PendingDelivery(operation.generation, waiter, result)
        }
    }.also { operation.waiters.clear() }

    private fun enqueueDeliveriesLocked(deliveries: Collection<PendingDelivery>) {
        if (deliveries.isEmpty()) return
        synchronized(deliveryLock) { pendingDeliveries.addAll(deliveries) }
    }

    private fun convertQueuedDeliveriesToSupersededLocked(currentGeneration: Long) {
        synchronized(deliveryLock) {
            pendingDeliveries.forEach { delivery ->
                if (delivery.generation != currentGeneration) {
                    delivery.result = RemoteConfigFetchResult.Superseded
                }
            }
        }
    }

    private fun drainDeliveries() {
        val ownsDrain = synchronized(deliveryLock) {
            if (isDrainingDeliveries) {
                false
            } else {
                isDrainingDeliveries = true
                true
            }
        }
        if (!ownsDrain) return
        while (true) {
            val delivery = synchronized(deliveryLock) {
                pendingDeliveries.pollFirst() ?: run {
                    isDrainingDeliveries = false
                    return
                }
            }
            delivery.waiter.timeoutTask?.cancelSafely()
            try {
                delivery.waiter.callback(delivery.result)
            } catch (_: Throwable) {
                // One consumer cannot undo a committed transition or starve claimed waiters.
            }
        }
    }

    private fun retryableFailureState(response: RemoteConfigFetchResponse.Failure): RemoteConfigFetchPolicyState {
        val now = nowMillis()
        val failureCount = (policyState.consecutiveRetryableFailures + 1).coerceAtMost(MAX_FAILURE_COUNT)
        val jitterCap = exponentialBackoffCap(failureCount)
        val randomValue = try {
            random.nextDouble()
        } catch (_: Throwable) {
            SAFE_FALLBACK_JITTER
        }
        val jitter = randomValue.takeIf { it.isFinite() && it >= 0.0 && it < 1.0 }
            ?: SAFE_FALLBACK_JITTER
        val delay = response.retryAfterMillis
            ?.takeIf { it >= 0 }
            ?.coerceAtMost(policy.maximumBackoffMillis)
            ?: (jitterCap.toDouble() * jitter).toLong().coerceAtLeast(MINIMUM_NONZERO_JITTER_MILLIS)
        return policyState.copy(
            consecutiveRetryableFailures = failureCount,
            nextAllowedFetchAtMillis = saturatingAdd(now, delay),
        )
    }

    private fun exponentialBackoffCap(failureCount: Int): Long {
        var result = policy.initialBackoffMillis
        repeat((failureCount - 1).coerceAtLeast(0)) {
            result = if (result >= policy.maximumBackoffMillis / 2) {
                policy.maximumBackoffMillis
            } else {
                (result * 2).coerceAtMost(policy.maximumBackoffMillis)
            }
        }
        return result
    }

    private fun shouldApplyMinimumInterval(forceReason: RemoteConfigFetchForceReason?, now: Long): Boolean {
        if (forceReason != null || policyState.lastSuccessfulFetchAtMillis <= 0 ||
            now < policyState.lastSuccessfulFetchAtMillis
        ) {
            return false
        }
        return now < saturatingAdd(
            policyState.lastSuccessfulFetchAtMillis,
            policy.minimumFetchIntervalMillis,
        )
    }

    @Suppress("ReturnCount")
    private fun loadPolicyState(scope: RemoteConfigFetchPolicyScope): LoadedPolicyState {
        val loaded = try {
            policyStore.load(scope)
        } catch (_: Throwable) {
            null
        } ?: return LoadedPolicyState(RemoteConfigFetchPolicyState())
        val latestBoundedDeadline = saturatingAdd(nowMillis(), policy.maximumBackoffMillis)
        if (loaded.nextAllowedFetchAtMillis <= latestBoundedDeadline) return LoadedPolicyState(loaded)
        val sanitized = loaded.copy(nextAllowedFetchAtMillis = latestBoundedDeadline)
        return LoadedPolicyState(
            state = sanitized,
            persistenceFailure = scope.takeUnless { savePolicyState(scope, sanitized) },
        )
    }

    private fun savePolicyState(
        scope: RemoteConfigFetchPolicyScope,
        state: RemoteConfigFetchPolicyState,
    ): Boolean = try {
        policyStore.save(scope, state)
    } catch (_: Throwable) {
        false
    }

    private fun observePolicyPersistenceFailure(scope: RemoteConfigFetchPolicyScope) {
        try {
            policyPersistenceFailureObserver(scope)
        } catch (_: Throwable) {
            // Telemetry cannot alter the conservative in-process guard.
        }
    }

    private fun nowMillis(): Long = try {
        clock.nowMillis().coerceAtLeast(0)
    } catch (_: Throwable) {
        0
    }

    private fun RemoteConfigFetchScheduledTask.cancelSafely() {
        try {
            cancel()
        } catch (_: Throwable) {
            // Terminal claiming is independent of best-effort timer cancellation.
        }
    }

    private fun RemoteConfigFetchResponse.Failure.isRetryable(): Boolean =
        statusCode == HTTP_TOO_MANY_REQUESTS || statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

    private fun InFlight.isCurrentLocked(attemptOrdinal: Long): Boolean =
        inFlight === this && generation == operationGeneration && this.attemptOrdinal == attemptOrdinal

    private fun isLiveWaiterLocked(operation: InFlight, waiter: FetchWaiter): Boolean {
        val operationIsCurrent = inFlight === operation && operation.generation == operationGeneration
        return operationIsCurrent && !waiter.terminalClaimed && operation.waiters.contains(waiter)
    }

    private data class ResponseOutcome(
        val result: RemoteConfigFetchResult,
        val nextPolicyState: RemoteConfigFetchPolicyState? = null,
    )

    private data class LoadedPolicyState(
        val state: RemoteConfigFetchPolicyState,
        val persistenceFailure: RemoteConfigFetchPolicyScope? = null,
    )

    private data class FetchDecision(
        val generation: Long,
        val immediateResult: RemoteConfigFetchResult? = null,
        val operation: InFlight? = null,
        val waiter: FetchWaiter? = null,
        val shouldStart: Boolean = false,
    ) {
        companion object {
            fun immediate(generation: Long, result: RemoteConfigFetchResult) =
                FetchDecision(generation = generation, immediateResult = result)

            fun joined(generation: Long, operation: InFlight, waiter: FetchWaiter) = FetchDecision(
                generation = generation,
                operation = operation,
                waiter = waiter,
            )

            fun started(generation: Long, operation: InFlight, waiter: FetchWaiter) = FetchDecision(
                generation = generation,
                operation = operation,
                waiter = waiter,
                shouldStart = true,
            )
        }
    }

    private enum class NotModifiedDisposition {
        NotApplicable,
        Accept,
        Retry,
        Reject,
        Ignore,
    }

    private data class InFlight(
        val generation: Long,
        val scope: RemoteConfigSnapshotScope,
        var admission: RemoteConfigSnapshotAdmissionToken,
        val waiters: MutableList<FetchWaiter>,
        var conditionalValidator: RemoteConfigConditionalRequestValidator?,
        var attemptOrdinal: Long = 0,
        var didRetryWithoutETag: Boolean = false,
    ) {
        val policyScope: RemoteConfigFetchPolicyScope = RemoteConfigFetchPolicyScope.from(scope)
    }

    private class FetchWaiter(
        val callback: (RemoteConfigFetchResult) -> Unit,
        var timeoutTask: RemoteConfigFetchScheduledTask? = null,
        var terminalClaimed: Boolean = false,
    )

    private data class PendingDelivery(
        val generation: Long,
        val waiter: FetchWaiter,
        var result: RemoteConfigFetchResult,
    )

    private companion object {
        const val MAX_FAILURE_COUNT = 63
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR_MIN = 500
        const val HTTP_SERVER_ERROR_MAX = 599
        const val SAFE_FALLBACK_JITTER = 0.5
        const val MINIMUM_NONZERO_JITTER_MILLIS = 1L

        fun saturatingAdd(left: Long, right: Long): Long =
            if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

        fun nextGeneration(current: Long): Long = if (current == Long.MAX_VALUE) 0 else current + 1
    }
}
