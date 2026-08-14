package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal const val REMOTE_CONFIG_ACK_MAX_ATTEMPTS = 3
internal const val REMOTE_CONFIG_ACK_INITIAL_RETRY_DELAY_MILLIS = 1_000L
internal const val REMOTE_CONFIG_ACK_MAXIMUM_RETRY_DELAY_MILLIS = 30_000L

private const val REMOTE_CONFIG_ACK_PREFIX = "qonversion_remote_config_v2_ack_"
private const val REMOTE_CONFIG_ACK_VERSION = 1
private const val REMOTE_CONFIG_ACK_MAX_BYTES = 1024
private const val MILLIS_PER_SECOND = 1_000L
private const val MINIMUM_RETRY_DELAY_MILLIS = 1L
private const val SAFE_FALLBACK_JITTER = 0.5

/**
 * One activation the app owes the gateway an acknowledgement for.
 *
 * [activatedAtSeconds] is stamped when the activation happened, NOT when the ack is finally sent:
 * a queued ack can outlive several retries and a process restart, and the server is being told
 * when the release started serving.
 */
internal data class RemoteConfigActivationAck(
    val releaseNumber: Long,
    val activatedAtSeconds: Long,
)

/**
 * The durable ack bookkeeping of one identity scope.
 *
 * [settledReleaseNumber] is what makes the "exactly one ack per (scope, release)" promise survive a
 * restart: without it every cold start would re-ack the release it activates from persisted state.
 * A release is settled once the gateway either accepted the ack or refused it permanently — both
 * are answers, and neither is worth asking again.
 */
internal data class RemoteConfigActivationAckRecord(
    val pending: RemoteConfigActivationAck?,
    val settledReleaseNumber: Long,
)

internal interface RemoteConfigActivationAckStore {
    fun load(scope: RemoteConfigSnapshotScope): RemoteConfigActivationAckRecord?
    fun save(scope: RemoteConfigSnapshotScope, record: RemoteConfigActivationAckRecord): Boolean
    fun clear(scope: RemoteConfigSnapshotScope): Boolean
}

internal sealed class RemoteConfigAckResponse {
    /** The gateway accepted the ack (`204`, and any other `2xx`). */
    data object Delivered : RemoteConfigAckResponse()

    /** Retrying can only repeat the same answer — the ack is abandoned. */
    data object Permanent : RemoteConfigAckResponse()

    /** A transport fault or a `429`/`5xx`: worth one more bounded attempt. */
    data object Retryable : RemoteConfigAckResponse()

    /**
     * The transport no longer addresses the identity the ack was queued for. No attempt was made,
     * so it costs no retry budget and the queued ack stays durable for the next binding.
     */
    data object NotAddressable : RemoteConfigAckResponse()
}

internal fun interface RemoteConfigAckTransport {
    fun sendAck(
        scope: RemoteConfigSnapshotScope,
        ack: RemoteConfigActivationAck,
        completion: (RemoteConfigAckResponse) -> Unit,
    )
}

/**
 * Reports every activation that changed the served release, exactly once per (scope, release).
 *
 * Hard rules, in the order they matter:
 * 1. **It can never affect the config data path.** Nothing here calls back into the app, blocks an
 *    activation, or feeds the fetch policy. Every failure is silent; the only externally visible
 *    trace of a lost ack is [droppedAckCount], which is a counter rather than a log line so a
 *    flapping gateway cannot turn into a log storm.
 * 2. **At most one ack is in flight per scope, and the newest activation wins.** A later activation
 *    supersedes an older queued or in-flight one: the server wants to know which release is serving
 *    now, and re-sending the intermediate ones would be a request storm for no information.
 * 3. **A queued ack is durable.** It is persisted before the first attempt and cleared only when
 *    delivered, permanently refused, or superseded — so a process death between activation and
 *    delivery does not lose it.
 * 4. **Retries are bounded per process, not per binding.** [maxAttempts] attempts with
 *    exponentially growing, jittered delays, then delivery of that release is abandoned for the
 *    lifetime of the process — a rebind cannot buy it another three. The durable record survives
 *    the abandonment, so the next process start tries once more, and only a newer release re-arms
 *    delivery inside this one.
 *
 * The whole object only exists when the app configured Remote Config v2 (see
 * [RemoteConfigV2Factory]), which is what keeps the feature dormant otherwise.
 */
@Suppress("LongParameterList", "TooManyFunctions")
internal class RemoteConfigActivationAckSender(
    private val transport: RemoteConfigAckTransport,
    private val store: RemoteConfigActivationAckStore,
    private val clock: RemoteConfigFetchClock,
    private val random: RemoteConfigFetchRandom,
    private val scheduler: RemoteConfigFetchScheduler,
    private val maxAttempts: Int = REMOTE_CONFIG_ACK_MAX_ATTEMPTS,
    private val initialRetryDelayMillis: Long = REMOTE_CONFIG_ACK_INITIAL_RETRY_DELAY_MILLIS,
    private val maximumRetryDelayMillis: Long = REMOTE_CONFIG_ACK_MAXIMUM_RETRY_DELAY_MILLIS,
) {
    private val lock = Any()
    private val storeLock = Any()
    private val dropped = AtomicLong()
    private var boundScope: RemoteConfigSnapshotScope? = null
    private var pending: RemoteConfigActivationAck? = null
    private var settledReleaseNumber = 0L
    private var generation = 0L
    private var inFlight = false
    private var attempt = 0
    private var retryScheduled = false
    private var retryTask: RemoteConfigFetchScheduledTask? = null
    private var writeStamp = 0L
    private var lastWrittenStamp = 0L

    /**
     * The (scope, release) whose retry ladder this process already exhausted.
     *
     * In memory on purpose: the bound on attempts is per process, so a rebind — an identify that
     * returns to an identity, a logout and back — must NOT buy the same release three more
     * attempts against a gateway that is failing. Only a NEWER release re-arms delivery, and a
     * genuinely new process reads the still-pending record and tries once more.
     */
    private var abandonedScope: RemoteConfigSnapshotScope? = null
    private var abandonedReleaseNumber = 0L

    /** Acks abandoned without delivery, ever. Deliberately a counter and not a log. */
    val droppedAckCount: Long get() = dropped.get()

    /**
     * Binds the sender to [scope] and resumes whatever ack that scope still owes.
     *
     * This is the restart path: the durable record is the only thing that survives a process, and
     * this is where it is read back. Binding also fences every in-flight and scheduled attempt of
     * the previous scope — one identity's session must never vouch for another's activation.
     */
    @Suppress("ReturnCount")
    fun bind(scope: RemoteConfigSnapshotScope?) {
        synchronized(lock) {
            if (isDeliveringForLocked(scope)) return
            invalidateLocked()
            boundScope = scope
            pending = null
            settledReleaseNumber = 0
            if (scope != null) {
                val record = loadRecord(scope)
                pending = record?.pending
                settledReleaseNumber = record?.settledReleaseNumber ?: 0
            }
        }
        startIfIdle()
    }

    /**
     * Queues an ack for the release that just became active in [scope].
     *
     * Idempotent by (scope, release): an already delivered release and an already queued one are
     * both no-ops, so the caller may report the same activation as often as it likes — which is how
     * an implicit (read-triggered) activation and an explicit `activate()` of the same release
     * still produce exactly one ack.
     */
    @Suppress("ReturnCount")
    fun recordActivation(scope: RemoteConfigSnapshotScope?, releaseNumber: Long) {
        if (scope == null || releaseNumber <= 0) return
        val write = synchronized(lock) {
            if (scope != boundScope) return
            if (releaseNumber == settledReleaseNumber) return
            if (pending?.releaseNumber == releaseNumber) return
            pending = RemoteConfigActivationAck(releaseNumber, nowSeconds())
            // The newest activation supersedes an older in-flight or scheduled one.
            invalidateLocked()
            prepareWriteLocked(scope)
        }
        // Durable BEFORE the first attempt, and outside the lock: the write ends in a synchronous
        // disk commit, and no other thread may be parked on the sender while it runs.
        flush(write)
        startIfIdle()
    }

    /**
     * Whether an ack for exactly [scope] is already being delivered.
     *
     * Re-binding such an identity changes nothing, and fencing it would re-send an ack whose answer
     * is merely still in flight — so an identify call that does not actually change the identity
     * costs no request.
     */
    private fun isDeliveringForLocked(scope: RemoteConfigSnapshotScope?): Boolean {
        if (scope == null || scope != boundScope) return false
        return inFlight || retryScheduled
    }

    @Suppress("ReturnCount")
    private fun startIfIdle() {
        val started = synchronized(lock) {
            val scope = boundScope ?: return
            val ack = pending ?: return
            if (inFlight || retryScheduled) return
            if (isAbandonedLocked(scope, ack)) return
            inFlight = true
            attempt = 1
            Attempt(generation, scope, ack)
        }
        dispatch(started)
    }

    private fun isAbandonedLocked(
        scope: RemoteConfigSnapshotScope,
        ack: RemoteConfigActivationAck,
    ): Boolean = scope == abandonedScope && ack.releaseNumber == abandonedReleaseNumber

    private fun dispatch(sending: Attempt) {
        try {
            transport.sendAck(sending.scope, sending.ack) { response -> onResponse(sending, response) }
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            onResponse(sending, RemoteConfigAckResponse.Retryable)
        }
    }

    @Suppress("ReturnCount")
    private fun onResponse(sent: Attempt, response: RemoteConfigAckResponse) {
        if (!sent.claim()) return
        var retryDelayMillis: Long? = null
        val write = synchronized(lock) {
            // A bind or a newer activation happened while this attempt was on the wire: its answer
            // says nothing about the state the sender is in now.
            if (sent.generation != generation || sent.scope != boundScope) return
            inFlight = false
            val write = when (response) {
                // Both outcomes SETTLE the release durably. A permanent refusal is settled rather
                // than forgotten on purpose: the likeliest one is a gateway that does not serve
                // /ack at all, and forgetting it would re-queue and re-POST the very same ack on
                // every process start and every identity binding, forever.
                RemoteConfigAckResponse.Delivered -> settleLocked(sent)
                RemoteConfigAckResponse.Permanent -> {
                    dropped.incrementAndGet()
                    settleLocked(sent)
                }
                // Not an attempt: the retry budget is untouched and the record stays queued.
                RemoteConfigAckResponse.NotAddressable -> null
                RemoteConfigAckResponse.Retryable -> if (attempt >= maxAttempts) {
                    dropped.incrementAndGet()
                    // Owed but abandoned for this process; the durable record is left untouched so
                    // the next process start delivers it.
                    abandonedScope = sent.scope
                    abandonedReleaseNumber = sent.ack.releaseNumber
                    null
                } else {
                    retryDelayMillis = retryDelayLocked(attempt)
                    null
                }
            }
            retryDelayMillis?.let { scheduleRetryLocked(it) }
            write
        }
        flush(write)
    }

    private fun settleLocked(sent: Attempt): PendingWrite {
        settledReleaseNumber = maxOf(settledReleaseNumber, sent.ack.releaseNumber)
        if (pending?.releaseNumber == sent.ack.releaseNumber) pending = null
        return prepareWriteLocked(sent.scope)
    }

    private fun scheduleRetryLocked(delayMillis: Long) {
        val scheduledGeneration = generation
        retryScheduled = true
        retryTask = try {
            scheduler.schedule(delayMillis) { onRetryDue(scheduledGeneration) }
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            retryScheduled = false
            dropped.incrementAndGet()
            null
        }
    }

    @Suppress("ReturnCount")
    private fun onRetryDue(scheduledGeneration: Long) {
        val next = synchronized(lock) {
            if (scheduledGeneration != generation) return
            retryScheduled = false
            retryTask = null
            val scope = boundScope ?: return
            val ack = pending ?: return
            if (inFlight) return
            attempt += 1
            inFlight = true
            Attempt(generation, scope, ack)
        }
        dispatch(next)
    }

    /**
     * Fences everything in flight or scheduled.
     *
     * Only the in-memory delivery is invalidated; the durable record is untouched, because the ack
     * it holds is still owed.
     */
    private fun invalidateLocked() {
        generation++
        retryScheduled = false
        try {
            retryTask?.cancel()
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            // Generation fencing, not cancellation, is what makes a stale timer harmless.
        }
        retryTask = null
        inFlight = false
        attempt = 0
    }

    private fun retryDelayLocked(attemptOrdinal: Int): Long {
        var cap = initialRetryDelayMillis
        repeat((attemptOrdinal - 1).coerceAtLeast(0)) {
            cap = if (cap >= maximumRetryDelayMillis / 2) {
                maximumRetryDelayMillis
            } else {
                (cap * 2).coerceAtMost(maximumRetryDelayMillis)
            }
        }
        val randomValue = try {
            random.nextDouble()
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            SAFE_FALLBACK_JITTER
        }
        val jitter = randomValue.takeIf { it.isFinite() && it >= 0.0 && it < 1.0 } ?: SAFE_FALLBACK_JITTER
        // Half the cap plus jitter, not full-downward jitter: the latter can put all three attempts
        // inside a few milliseconds, which is the storm the bound exists to prevent.
        val half = cap / 2
        return (half + (half.toDouble() * jitter).toLong()).coerceAtLeast(MINIMUM_RETRY_DELAY_MILLIS)
    }

    private fun prepareWriteLocked(scope: RemoteConfigSnapshotScope) = PendingWrite(
        scope = scope,
        record = RemoteConfigActivationAckRecord(pending, settledReleaseNumber),
        stamp = ++writeStamp,
    )

    /**
     * Writes a prepared record, outside [lock] so a synchronous disk commit can never park the
     * thread that is activating or fetching.
     *
     * The stamp is what keeps two concurrent writers from committing out of order: a write that
     * was prepared before the last committed one is dropped rather than allowed to resurrect it.
     */
    private fun flush(write: PendingWrite?) {
        if (write == null) return
        synchronized(storeLock) {
            if (write.stamp <= lastWrittenStamp) return
            lastWrittenStamp = write.stamp
            try {
                if (write.record.pending == null && write.record.settledReleaseNumber <= 0) {
                    store.clear(write.scope)
                } else {
                    store.save(write.scope, write.record)
                }
            } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
                // The in-memory record still governs this process; a lost write can at worst cost
                // one duplicate ack after a restart, which the gateway must tolerate anyway.
            }
        }
    }

    private class PendingWrite(
        val scope: RemoteConfigSnapshotScope,
        val record: RemoteConfigActivationAckRecord,
        val stamp: Long,
    )

    private fun loadRecord(scope: RemoteConfigSnapshotScope): RemoteConfigActivationAckRecord? = try {
        store.load(scope)
    } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
        null
    }

    /**
     * The activation timestamp, floored at 1: a zero would be indistinguishable from "absent" in
     * the durable record and would make the queued ack silently un-persistable.
     */
    private fun nowSeconds(): Long = try {
        clock.nowMillis().coerceAtLeast(0) / MILLIS_PER_SECOND
    } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
        0
    }.coerceAtLeast(1)

    /**
     * One delivery attempt.
     *
     * [claim] makes the completion single-shot independently of the transport: a transport that
     * both calls back and throws must not be able to advance the retry budget twice.
     */
    private class Attempt(
        val generation: Long,
        val scope: RemoteConfigSnapshotScope,
        val ack: RemoteConfigActivationAck,
    ) {
        private val answered = AtomicBoolean(false)

        fun claim(): Boolean = answered.compareAndSet(false, true)
    }
}

/**
 * Durable, per-identity-scope ack bookkeeping.
 *
 * Mirrors [PersistentRemoteConfigSessionStore]: the storage key is a salted digest of the scope, so
 * neither the project key nor the canonical user id ever lands in a preference name.
 */
internal class PersistentRemoteConfigActivationAckStore(
    private val cache: Cache,
    moshi: Moshi,
) : RemoteConfigActivationAckStore {
    private val adapter = moshi.adapter(PersistedRemoteConfigActivationAck::class.java)

    @Synchronized
    @Suppress("ReturnCount")
    override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigActivationAckRecord? {
        val storageKey = remoteConfigAckStorageKey(scope)
        val raw = try {
            cache.getString(storageKey, null)
        } catch (_: Exception) {
            null
        } ?: return null
        val persisted = try {
            raw.takeIf { it.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_ACK_MAX_BYTES }
                ?.let(adapter::fromJson)
        } catch (_: Exception) {
            null
        }
        if (persisted == null || !persisted.isValid()) {
            removeInvalid(storageKey)
            return null
        }
        return RemoteConfigActivationAckRecord(
            pending = persisted.pendingReleaseNumber
                .takeIf { it > 0 }
                ?.let { RemoteConfigActivationAck(it, persisted.pendingActivatedAtSeconds) },
            settledReleaseNumber = persisted.settledReleaseNumber,
        )
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun save(scope: RemoteConfigSnapshotScope, record: RemoteConfigActivationAckRecord): Boolean {
        val persisted = PersistedRemoteConfigActivationAck(
            version = REMOTE_CONFIG_ACK_VERSION,
            pendingReleaseNumber = record.pending?.releaseNumber ?: 0,
            pendingActivatedAtSeconds = record.pending?.activatedAtSeconds ?: 0,
            settledReleaseNumber = record.settledReleaseNumber,
        )
        if (!persisted.isValid()) return false
        val raw = try {
            adapter.toJson(persisted)
        } catch (_: Exception) {
            return false
        }
        if (raw.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_ACK_MAX_BYTES) return false
        return try {
            cache.updateStringsDurably(
                values = mapOf(remoteConfigAckStorageKey(scope) to raw),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    override fun clear(scope: RemoteConfigSnapshotScope): Boolean = try {
        cache.updateStringsDurably(emptyMap(), setOf(remoteConfigAckStorageKey(scope)))
    } catch (_: Exception) {
        false
    }

    private fun PersistedRemoteConfigActivationAck.isValid(): Boolean =
        version == REMOTE_CONFIG_ACK_VERSION &&
            pendingReleaseNumber >= 0 &&
            settledReleaseNumber >= 0 &&
            pendingActivatedAtSeconds >= 0 &&
            (pendingReleaseNumber == 0L || pendingActivatedAtSeconds > 0)

    private fun removeInvalid(key: String) {
        try {
            cache.updateStringsDurably(emptyMap(), setOf(key))
        } catch (_: Exception) {
            // A malformed record stays untrusted even when best-effort cleanup fails.
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigActivationAck(
    val version: Int,
    @Json(name = "pending_release_number")
    val pendingReleaseNumber: Long,
    @Json(name = "pending_activated_at")
    val pendingActivatedAtSeconds: Long,
    @Json(name = "settled_release_number")
    val settledReleaseNumber: Long,
)

private fun remoteConfigAckStorageKey(scope: RemoteConfigSnapshotScope): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-activation-ack-v1".encodeToByteArray())
    digest.updateLengthPrefixed(scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(scope.environment.encodeToByteArray())
    digest.updateLengthPrefixed(scope.canonicalUserId.encodeToByteArray())
    return REMOTE_CONFIG_ACK_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
    update(value)
}
