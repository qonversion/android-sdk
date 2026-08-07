@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSnapshot
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSubscription
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate
import com.qonversion.android.sdk.internal.logger.Logger
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val REMOTE_CONFIG_V2_DEFAULT_FETCH_TIMEOUT_MILLIS = 5_000L

/**
 * Immutable addressing of one Remote Config v2 integration.
 *
 * The server-resolved targeting context is deliberately not part of it. The fingerprint hashes
 * mutable targeting context (app/OS version, locale, purchases, properties); it rotates legitimately
 * and MUST NOT be pinned across fetches. Identity isolation is the session's job — see
 * [RemoteConfigGatewaySession] and the per-scope storage keys.
 *
 * The numeric project id is not part of it either: the SDK learns it from the session bootstrap
 * rather than from the app, and it travels with the response it addresses.
 */
internal data class RemoteConfigV2Options(
    val projectKey: String,
    val environmentUid: String,
)

/**
 * The identity scope the transport addresses, published for the transport's identity provider.
 *
 * The transport is constructed before any identity is known, so it reads the scope through this
 * holder instead of capturing one.
 */
internal class RemoteConfigV2ScopeHolder {
    private val current = AtomicReference<RemoteConfigSnapshotScope?>(null)

    var scope: RemoteConfigSnapshotScope?
        get() = current.get()
        set(value) = current.set(value)
}

internal fun interface RemoteConfigMainDispatcher {
    /** Runs [action] on the main thread, inline when the caller is already on it. */
    fun post(action: () -> Unit)

    /**
     * Runs [action] on the main thread, never inline.
     *
     * Used for app-supplied listeners: a snapshot activation can be committed *from* the main
     * thread (the read guard's implicit activation), and running a listener inline there would
     * execute app code while the core still holds its delivery-drain ownership — a listener that
     * touches another Qonversion API from there can deadlock against an identity transition.
     */
    fun postDeferred(action: () -> Unit) = post(action)
}

/**
 * Binds the Remote Config v2 internals — snapshot core, read guard, fetch coordinator and gateway
 * transport — into the operations the public [com.qonversion.android.sdk.QRemoteConfigSnapshots] surface
 * exposes.
 *
 * Threading contract:
 * - every completion is delivered exactly once through [mainDispatcher];
 * - every operation that can touch durable storage runs on [worker], which MUST be the same
 *   single-threaded executor the read guard's preloader uses. That ordering is what keeps a scope
 *   transition from racing its own preload: the preload task is enqueued first and therefore
 *   installs the loaded state before the coordinator observes the new scope.
 */
@Suppress("LongParameterList")
internal class RemoteConfigV2Manager(
    private val core: RemoteConfigSnapshotCore,
    private val readGuard: RemoteConfigReadGuard,
    private val coordinator: RemoteConfigFetchCoordinator,
    private val ackSender: RemoteConfigActivationAckSender,
    private val options: RemoteConfigV2Options,
    private val scopeHolder: RemoteConfigV2ScopeHolder,
    private val scheduler: RemoteConfigFetchScheduler,
    private val worker: Executor,
    private val mainDispatcher: RemoteConfigMainDispatcher,
    private val logger: Logger,
    private val defaultFetchTimeoutMillis: Long = REMOTE_CONFIG_V2_DEFAULT_FETCH_TIMEOUT_MILLIS,
) {
    /**
     * The (scope, release) this manager has already handed to the ack sender, so an ordinary
     * `current` read costs one reference compare instead of a worker task. It is a cache of the
     * sender's own idempotency, never a substitute for it: the sender is the only thing that
     * decides whether an ack is actually owed.
     *
     * The scope is part of it precisely because the cache is written from the caller's thread: a
     * read that started before an identity change can land after it, and a bare release number
     * would then suppress the new identity's ack for the same release number.
     */
    private val notedActivation = AtomicReference<NotedActivation?>(null)

    private data class NotedActivation(
        val scope: RemoteConfigSnapshotScope,
        val releaseNumber: Long,
    )

    /**
     * Switches the served scope to [canonicalUserId] and kicks off a forced fetch.
     *
     * The scope swap is performed synchronously on the calling thread, so the previous identity's
     * snapshot stops being readable before this call returns — a read that races an identity
     * change can only ever see the new (initially fallback-only) scope, never the old release.
     */
    fun updateIdentity(canonicalUserId: String, forceReason: RemoteConfigFetchForceReason) {
        val scope = scopeFor(canonicalUserId)
        // Order matters: the core stops accepting admissions for the previous scope BEFORE the
        // transport starts addressing the new one. The reverse order leaves a window in which a
        // concurrent fetch reads the new identity and admits its snapshot into the old store.
        readGuard.transitionScopeBeforeSdkReady(scope)
        scopeHolder.scope = scope
        val submitted = submit {
            coordinator.transitionTo(scope)
            // Binds the ack queue to the new identity — and, on the first identity of a process,
            // resumes an ack an earlier process activated but never managed to deliver.
            notedActivation.set(null)
            ackSender.bind(scope)
            if (scope != null) forceFetch(forceReason)
        }
        if (!submitted) logger.debug("Remote Config v2 could not apply an identity change")
    }

    /**
     * Re-reads targeting for the *same* identity, e.g. after user properties or an attached
     * experiment changed the evaluation inputs.
     *
     * Deliberately not a scope transition: the identity did not change, so the served release must
     * keep serving until a newer one is fetched and activated.
     */
    fun refreshTargeting() {
        if (scopeHolder.scope == null) return
        submit { forceFetch(RemoteConfigFetchForceReason.Identify) }
    }

    private fun forceFetch(forceReason: RemoteConfigFetchForceReason) {
        // No caller is waiting, so no timeout is armed — the request runs to its own completion.
        fetch(timeoutMillis = 0, forceReason = forceReason) { result ->
            if (result.status != QRemoteConfigFetchStatus.Fetched &&
                result.status != QRemoteConfigFetchStatus.NotModified
            ) {
                logger.debug("Remote Config v2 forced fetch ended as ${result.status}")
            }
        }
    }

    val current: QRemoteConfigSnapshot get() {
        val snapshot = readGuard.currentSnapshot()
        // A read can itself activate (the guard's one-shot implicit activation in release builds),
        // and that activation is exactly as ack-worthy as an explicit one.
        noteActivatedRelease(snapshot.releaseNumber)
        return QRemoteConfigSnapshot(snapshot)
    }

    /**
     * Fetches a release and completes with the best available data.
     *
     * [timeoutMillis] bounds the *wait*, not the request: when it elapses the completion fires with
     * [QRemoteConfigFetchStatus.TimedOut] and the request keeps running, so a slow response is
     * still admitted and offered to the next activation.
     */
    fun fetch(
        timeoutMillis: Long?,
        forceReason: RemoteConfigFetchForceReason? = null,
        callback: (QRemoteConfigFetchResult) -> Unit,
    ) {
        val delivery = SingleDelivery(callback)
        val timeoutTask = scheduleTimeout(timeoutMillis, delivery)
        val submitted = submit {
            coordinator.fetch(forceReason) { result ->
                timeoutTask.cancelSafely()
                delivery.deliver(result.toPublicResult())
            }
        }
        if (!submitted) {
            timeoutTask.cancelSafely()
            delivery.deliver(result(QRemoteConfigFetchStatus.Failed))
        }
    }

    /** Swaps the last fetched release into [current] atomically, on the worker thread. */
    fun activate(fetchStatus: QRemoteConfigFetchStatus? = null, callback: (QRemoteConfigActivationResult) -> Unit) {
        val delivery = SingleDelivery(callback)
        val submitted = submit {
            val transition = try {
                readGuard.activate()
            } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
                logger.debug("Remote Config v2 activation failed: ${error.javaClass.simpleName}")
                RemoteConfigSnapshotTransitionResult(RemoteConfigSnapshotTransitionStatus.Ignored)
            }
            if (transition.status == RemoteConfigSnapshotTransitionStatus.PersistenceFailed) {
                // The app keeps serving the previously activated release; say so, because
                // `changed = false` alone is indistinguishable from "there was nothing new".
                logger.error("Remote Config v2 activation could not be persisted")
            }
            val changed = transition.status == RemoteConfigSnapshotTransitionStatus.Activated && transition.changed
            val snapshot = core.currentSnapshot()
            delivery.deliver(
                QRemoteConfigActivationResult(
                    changed = changed,
                    snapshot = QRemoteConfigSnapshot(snapshot),
                    fetchStatus = fetchStatus,
                ),
            )
            // Strictly after the completion is handed off, and on a later worker task: the ack
            // queue does durable I/O and the activation contract promises none of it.
            //
            // `Unchanged` is included deliberately. It means "this release is already the active
            // one" — which is the shape an activation takes when the read guard activated it
            // implicitly first, and that release is owed exactly the same ack.
            if (transition.status == RemoteConfigSnapshotTransitionStatus.Activated ||
                transition.status == RemoteConfigSnapshotTransitionStatus.Unchanged
            ) {
                noteActivatedRelease(snapshot.releaseNumber)
            }
        }
        if (!submitted) {
            delivery.deliver(
                QRemoteConfigActivationResult(
                    changed = false,
                    snapshot = QRemoteConfigSnapshot(core.currentSnapshot()),
                    fetchStatus = fetchStatus,
                ),
            )
        }
    }

    fun fetchAndActivate(timeoutMillis: Long?, callback: (QRemoteConfigActivationResult) -> Unit) {
        fetch(timeoutMillis) { fetchResult ->
            activate(fetchResult.status, callback)
        }
    }

    fun subscribeOnConfigUpdate(listener: (QRemoteConfigUpdate) -> Unit): QRemoteConfigSubscription {
        val token = core.addUpdateObserver { update ->
            mainDispatcher.postDeferred { listener(QRemoteConfigUpdate(update)) }
        }
        return QRemoteConfigSubscription { core.removeUpdateObserver(token) }
    }

    /**
     * Offers [releaseNumber] to the ack queue, off the caller's thread.
     *
     * Always asynchronous, including when it is already called from the worker: queueing an ack
     * writes to durable storage, and neither `activate()`'s completion nor a `current` read may pay
     * for that. The scope is captured here rather than inside the task so a task that lands after
     * an identity change is dropped by the sender instead of acking the wrong identity.
     */
    @Suppress("ReturnCount")
    private fun noteActivatedRelease(releaseNumber: Long) {
        if (releaseNumber <= 0) return
        val scope = scopeHolder.scope ?: return
        val noted = NotedActivation(scope, releaseNumber)
        if (notedActivation.getAndSet(noted) == noted) return
        // A rejected submit must not leave the activation marked as handed off, or the ack would be
        // lost for good; the next read or activation of the same release then offers it again.
        if (!submit { ackSender.recordActivation(scope, releaseNumber) }) {
            notedActivation.compareAndSet(noted, null)
        }
    }

    private fun scopeFor(canonicalUserId: String): RemoteConfigSnapshotScope? = try {
        RemoteConfigSnapshotScope(
            projectKey = options.projectKey,
            environment = options.environmentUid,
            canonicalUserId = canonicalUserId,
        )
    } catch (_: IllegalArgumentException) {
        logger.debug("Remote Config v2 identity is not addressable")
        null
    }

    private fun scheduleTimeout(
        timeoutMillis: Long?,
        delivery: SingleDelivery<QRemoteConfigFetchResult>,
    ): RemoteConfigFetchScheduledTask? {
        val effective = (timeoutMillis ?: defaultFetchTimeoutMillis).takeIf { it > 0 } ?: return null
        return try {
            scheduler.schedule(effective) {
                delivery.deliver(result(QRemoteConfigFetchStatus.TimedOut))
            }
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            null
        }
    }

    /**
     * The snapshot a completion reports: freshly fetched when there is one, otherwise the
     * activated release, otherwise the bundled defaults.
     *
     * It deliberately reads the core rather than the read guard — a completion is an explicit
     * hand-off of data the app asked for, not an implicit `current` read, so it must not consume
     * the guard's one-shot read-before-activate opportunity.
     */
    private fun bestAvailableSnapshot(): QRemoteConfigSnapshot =
        QRemoteConfigSnapshot(core.lastFetchedSnapshot() ?: core.currentSnapshot())

    private fun result(status: QRemoteConfigFetchStatus) =
        QRemoteConfigFetchResult(status, bestAvailableSnapshot())

    private fun RemoteConfigFetchResult.toPublicResult(): QRemoteConfigFetchResult = when (this) {
        is RemoteConfigFetchResult.Fetched -> result(transition.toFetchStatus())
        RemoteConfigFetchResult.NotModified -> result(QRemoteConfigFetchStatus.NotModified)
        is RemoteConfigFetchResult.Failed -> result(QRemoteConfigFetchStatus.Failed)
        is RemoteConfigFetchResult.MinimumInterval -> result(QRemoteConfigFetchStatus.Throttled)
        is RemoteConfigFetchResult.Backoff -> result(QRemoteConfigFetchStatus.Throttled)
        // The coordinator's backstop timeout reports the activated snapshot only; the public
        // contract promises the fetched -> cache -> fallback ladder on every completion.
        is RemoteConfigFetchResult.TimedOut -> result(QRemoteConfigFetchStatus.TimedOut)
        // The release was admitted (or refused) exactly as any other outcome; only the fetch
        // bookkeeping could not be persisted, which the next attempt re-derives.
        is RemoteConfigFetchResult.PolicyPersistenceFailed -> result.toPublicResult()
        RemoteConfigFetchResult.InvalidNotModified -> result(QRemoteConfigFetchStatus.Failed)
        RemoteConfigFetchResult.Superseded -> result(QRemoteConfigFetchStatus.Superseded)
        // A permanent addressing fault the transport has already reported: no snapshot was read at
        // all, so there is nothing to report beyond the failure itself.
        RemoteConfigFetchResult.ProjectMismatch -> result(QRemoteConfigFetchStatus.Failed)
    }

    private fun RemoteConfigSnapshotTransitionResult.toFetchStatus(): QRemoteConfigFetchStatus = when (status) {
        // Rejected covers a malformed envelope AND a snapshot addressed to another project or
        // environment than the session it was served for. The latter is a permanent server-side
        // fault that otherwise looks exactly like a network failure. A changed targeting context is
        // NOT in this class: it rotates on any app/OS update, locale change, purchase or property
        // edit.
        RemoteConfigSnapshotTransitionStatus.Accepted,
        RemoteConfigSnapshotTransitionStatus.Activated,
        RemoteConfigSnapshotTransitionStatus.Unchanged,
        -> QRemoteConfigFetchStatus.Fetched
        RemoteConfigSnapshotTransitionStatus.Ignored -> QRemoteConfigFetchStatus.Superseded
        RemoteConfigSnapshotTransitionStatus.PersistenceFailed -> QRemoteConfigFetchStatus.Failed
        RemoteConfigSnapshotTransitionStatus.Rejected -> {
            logger.error(
                "Remote Config v2 refused a snapshot: it did not match the project id or " +
                    "environment uid of the session it was served for, or the envelope was malformed",
            )
            QRemoteConfigFetchStatus.Failed
        }
    }

    private fun submit(action: () -> Unit): Boolean = try {
        worker.execute {
            try {
                action()
            } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
                logger.debug("Remote Config v2 background work failed: ${error.javaClass.simpleName}")
            }
        }
        true
    } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
        false
    }

    private fun RemoteConfigFetchScheduledTask?.cancelSafely() {
        try {
            this?.cancel()
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            // Single-delivery is enforced independently of best-effort timer cancellation.
        }
    }

    private inner class SingleDelivery<T>(private val callback: (T) -> Unit) {
        private val delivered = AtomicBoolean(false)

        fun deliver(value: T) {
            if (!delivered.compareAndSet(false, true)) return
            mainDispatcher.post {
                try {
                    callback(value)
                } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
                    logger.debug("Remote Config v2 callback threw: ${error.javaClass.simpleName}")
                }
            }
        }
    }
}
