package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal const val REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS = 3
internal const val REMOTE_CONFIG_TELEMETRY_INITIAL_RETRY_DELAY_MILLIS = 1_000L
internal const val REMOTE_CONFIG_TELEMETRY_MAXIMUM_RETRY_DELAY_MILLIS = 30_000L

/** The coalescing map is bounded: a distinct entry beyond this is dropped, never queued. */
internal const val REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES = 64
internal const val REMOTE_CONFIG_TELEMETRY_FLUSH_THRESHOLD = 10
internal const val REMOTE_CONFIG_TELEMETRY_MAX_BATCH_EVENTS = 50
internal const val REMOTE_CONFIG_TELEMETRY_TICK_MILLIS = 30_000L
internal const val REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES = 200
internal const val REMOTE_CONFIG_TELEMETRY_MAX_COUNT = 100_000L

/**
 * Flush hygiene: an event this old, or this far in the future, is dropped when the batch is built.
 *
 * The gateway refuses a batch containing one out-of-window timestamp — and a `400` drops the WHOLE
 * batch permanently. A buffer that survived a month of offline process starts, or a device whose
 * clock jumped, must therefore not be able to poison every healthy event travelling with it. The
 * age bound is deliberately inside the server's 30-day window.
 */
internal const val REMOTE_CONFIG_TELEMETRY_MAX_AGE_SECONDS = 29L * 24 * 60 * 60
internal const val REMOTE_CONFIG_TELEMETRY_MAX_SKEW_SECONDS = 30L

/**
 * The wall clock is not trusted below this (≈ 2020-09).
 *
 * A device whose RTC has not been set yet reads somewhere near the epoch, which would make EVERY
 * buffered event look future-skewed and discard the whole buffer at the first flush. A clock this
 * implausible suspends pruning AND flushing until it becomes real, rather than being believed.
 */
internal const val REMOTE_CONFIG_TELEMETRY_CLOCK_FLOOR_SECONDS = 1_600_000_000L

/**
 * How many events may be held before any identity is bound.
 *
 * The read guard claims each of its events once per process, so an event produced before the first
 * `identify` — `read_before_activate` above all — is not merely delayed but lost for good if it is
 * dropped here. A small drop-oldest ring keeps it, without letting an app that never binds grow
 * memory.
 */
internal const val REMOTE_CONFIG_TELEMETRY_MAX_PRE_BIND_EVENTS = 16

/**
 * The durable record's byte budget.
 *
 * Sized for the composite bound the contract pins — the coalescing map
 * ([REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES]) plus one in-flight batch
 * ([REMOTE_CONFIG_TELEMETRY_MAX_BATCH_EVENTS]) — with every logical key at the contract's
 * [REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES] maximum.
 *
 * This implementation reaches that durability with a record that never exceeds the MAP, because an
 * in-flight batch is not removed from the map when it is dispatched: it is settled only when the
 * gateway answers. Every flush is therefore preceded by a write of a buffer that already contains
 * the batch, and a crash mid-flight loses nothing beyond the at-least-once redelivery the contract
 * already accepts. The budget is sized for the pinned bound regardless, because a budget that
 * silently turns a save into a no-op is the failure mode worth designing out.
 */
internal const val REMOTE_CONFIG_TELEMETRY_MAX_BYTES = 64 * 1024

private const val REMOTE_CONFIG_TELEMETRY_PREFIX = "qonversion_remote_config_v2_telemetry_"
private const val REMOTE_CONFIG_TELEMETRY_VERSION = 1
private const val MILLIS_PER_SECOND = 1_000L
private const val MINIMUM_RETRY_DELAY_MILLIS = 1L
private const val SAFE_FALLBACK_JITTER = 0.5

/**
 * The closed set of client telemetry kinds, exactly as the gateway spells them.
 *
 * The wire name is the contract: an unknown kind makes the gateway refuse the WHOLE batch with a
 * terminal 400, so the mapping lives here once rather than at every production site.
 */
internal enum class RemoteConfigTelemetryKind(val wireName: String) {
    DecodeFailure("decode_failure"),
    ReadBeforeActivate("read_before_activate"),
    ImplicitActivation("implicit_activation"),
    PreloadFailed("preload_failed"),
    PreloadCorrupt("preload_corrupt"),
    ActivationPersistenceFailed("activation_persistence_failed"),
    ;

    /** Only a decode failure names a key; every other kind MUST omit it. */
    internal val carriesLogicalKey: Boolean get() = this == DecodeFailure

    internal companion object {
        fun fromWireName(wireName: String): RemoteConfigTelemetryKind? =
            values().firstOrNull { it.wireName == wireName }
    }
}

/**
 * What distinguishes one coalescing bucket from another.
 *
 * The release number is deliberately NOT part of it: the gateway refuses a whole batch that carries
 * two events with the same (kind, logical_key), and a release rollover between two flushes is
 * exactly how a client would otherwise produce that pair. One bucket per (kind, key) therefore
 * makes the in-batch uniqueness the contract demands structural rather than incidental — the bucket
 * carries the release of its most recent occurrence instead.
 */
internal data class RemoteConfigTelemetryEventIdentity(
    val kind: RemoteConfigTelemetryKind,
    val logicalKey: String,
)

/** One coalesced bucket: how often it happened, when it last did, and under which release. */
internal data class RemoteConfigTelemetryEvent(
    val kind: RemoteConfigTelemetryKind,
    val logicalKey: String,
    val releaseNumber: Long,
    val count: Long,
    val lastOccurredAtSeconds: Long,
) {
    internal val identity: RemoteConfigTelemetryEventIdentity
        get() = RemoteConfigTelemetryEventIdentity(kind, logicalKey)

    /**
     * Whether the gateway would accept this event.
     *
     * Validated locally because the batch is rejected as a whole: one over-long logical key would
     * cost every other event in the same POST, so an unsendable event is dropped at the source.
     */
    internal fun isValid(): Boolean = releaseNumber >= 0 &&
        count in 1..REMOTE_CONFIG_TELEMETRY_MAX_COUNT &&
        lastOccurredAtSeconds > 0 &&
        if (kind.carriesLogicalKey) logicalKey.isSendableLogicalKey() else logicalKey.isEmpty()

    /**
     * Whether the gateway would accept this string as a `logical_key`.
     *
     * Validated on the UTF-8 BYTES, not on UTF-16 units, because that is the unit the server bounds
     * and rejects on: a key of 150 emoji is 150 units here and 600 bytes there, and a local check in
     * the wrong unit is exactly how a batch reaches the wire and comes back as a terminal 400.
     *
     * A string carrying an unpaired surrogate is refused rather than sent. `toByteArray` transcodes
     * one to `?` silently, so it would otherwise pass every byte-level check while the value that
     * reached the server was not the key the app actually read.
     */
    private fun String.isSendableLogicalKey(): Boolean {
        val bytes = toByteArray(Charsets.UTF_8)
        return bytes.size in 1..REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES &&
            bytes.none { byte -> byte >= 0 && (byte < CONTROL_BYTE_MAX || byte == DELETE_BYTE) } &&
            bytes.toString(Charsets.UTF_8) == this
    }

    /**
     * Folds [other] — an occurrence of the same (kind, logical_key) — into this bucket.
     *
     * Counts add, and the newer occurrence wins the release number and the timestamp: the batch may
     * carry only one event per (kind, key), and the release the LAST failure was served under is
     * the one the dashboard has to act on.
     */
    internal fun coalescedWith(other: RemoteConfigTelemetryEvent): RemoteConfigTelemetryEvent {
        val total = (count + other.count).coerceAtMost(REMOTE_CONFIG_TELEMETRY_MAX_COUNT)
        val newest = if (other.lastOccurredAtSeconds >= lastOccurredAtSeconds) other else this
        return newest.copy(count = total)
    }

    private companion object {
        const val CONTROL_BYTE_MAX: Byte = 0x20
        const val DELETE_BYTE: Byte = 0x7f
    }
}

/** The durable telemetry buffer of one identity scope. */
internal interface RemoteConfigTelemetryStore {
    fun load(scope: RemoteConfigSnapshotScope): List<RemoteConfigTelemetryEvent>
    fun save(scope: RemoteConfigSnapshotScope, events: List<RemoteConfigTelemetryEvent>): Boolean
    fun clear(scope: RemoteConfigSnapshotScope): Boolean
}

/**
 * Posts one batch out of band.
 *
 * The response vocabulary is [RemoteConfigAckResponse] rather than a private one: the telemetry
 * route mirrors the activation ack leg exactly — 2xx delivered, 400/404 permanent, 429/5xx
 * retryable, and "the transport no longer addresses this identity" costs no retry budget.
 */
internal fun interface RemoteConfigTelemetryTransport {
    fun postTelemetry(
        scope: RemoteConfigSnapshotScope,
        events: List<RemoteConfigTelemetryEvent>,
        completion: (RemoteConfigAckResponse) -> Unit,
    )
}

/**
 * Reports client-side Remote Config health — decode failures and read-guard events — to the gateway.
 *
 * Hard rules, in the order they matter:
 * 1. **It can never touch the config data path.** Every production site only enqueues into an
 *    in-memory map and returns; nothing here decodes a value, blocks a read, delays an activation
 *    or feeds the fetch policy. Every failure is silent, and the only externally visible trace is
 *    [droppedEventCount], a counter rather than a log line.
 * 2. **Memory is bounded, always.** At most [maxEntries] distinct (kind, key) buckets are held, plus
 *    a [maxPreBindEvents] ring for what happened before the first identity bound; a further distinct
 *    bucket is dropped rather than queued, so a pathological app that misdecodes a thousand keys
 *    costs 64 entries, not a thousand. Every drop, on every path, is counted in
 *    [droppedEventCount] — a silent loss is a bug, a counted one is a measurement.
 * 3. **A queued batch is durable, and storage is touched as rarely as it can be.** The buffer is
 *    written off the caller's thread and reloaded on the next process start, so events survive a
 *    process death — but a write only happens when the buffer actually CHANGED shape (a new bucket,
 *    a settled batch, a prune). A counter-only bump never reaches storage: the worker doing that
 *    write is the same single thread the snapshot preloader and the manager run on, and a
 *    synchronous preferences commit per config read would starve the config path through its queue.
 * 4. **Retries are bounded per process.** [maxAttempts] attempts with exponentially growing,
 *    jittered delays, then delivery is abandoned for the lifetime of the process for that identity.
 *    The durable buffer survives the abandonment, so the next process start tries once more.
 * 5. **A 401 never invalidates the session.** The stored session belongs to the config read path;
 *    an out-of-band signal may not forget it (the transport enforces this, see
 *    `RemoteConfigGatewayTransport.postTelemetry`).
 *
 * The whole object only exists when the app configured Remote Config v2 (see
 * [RemoteConfigV2Factory]), which is what keeps the feature dormant otherwise.
 */
@Suppress("LongParameterList", "TooManyFunctions")
internal class RemoteConfigTelemetrySender(
    private val transport: RemoteConfigTelemetryTransport,
    private val store: RemoteConfigTelemetryStore,
    private val clock: RemoteConfigFetchClock,
    private val random: RemoteConfigFetchRandom,
    private val scheduler: RemoteConfigFetchScheduler,
    private val executor: Executor,
    private val maxAttempts: Int = REMOTE_CONFIG_TELEMETRY_MAX_ATTEMPTS,
    private val initialRetryDelayMillis: Long = REMOTE_CONFIG_TELEMETRY_INITIAL_RETRY_DELAY_MILLIS,
    private val maximumRetryDelayMillis: Long = REMOTE_CONFIG_TELEMETRY_MAXIMUM_RETRY_DELAY_MILLIS,
    private val maxEntries: Int = REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES,
    private val flushThreshold: Int = REMOTE_CONFIG_TELEMETRY_FLUSH_THRESHOLD,
    private val maxBatchEvents: Int = REMOTE_CONFIG_TELEMETRY_MAX_BATCH_EVENTS,
    private val tickIntervalMillis: Long = REMOTE_CONFIG_TELEMETRY_TICK_MILLIS,
    private val maxPreBindEvents: Int = REMOTE_CONFIG_TELEMETRY_MAX_PRE_BIND_EVENTS,
) {
    private val lock = Any()
    private val storeLock = Any()
    private val dropped = AtomicLong()
    private val drainScheduled = AtomicBoolean(false)
    private val flushRequested = AtomicBoolean(false)
    private val writeStamp = AtomicLong()

    /** Insertion-ordered on purpose: the oldest bucket is the one a bounded batch takes first. */
    private val entries = LinkedHashMap<RemoteConfigTelemetryEventIdentity, RemoteConfigTelemetryEvent>()

    /**
     * Events produced before any identity was bound, oldest first.
     *
     * They cannot be sent — there is no session to send them under — but they must not be discarded
     * either: the read guard reports each of its events once per process, so a `read_before_activate`
     * dropped here is a systematic under-count of the exact metric the panel exists for.
     */
    private val preBind = ArrayDeque<RemoteConfigTelemetryEvent>()

    /**
     * Identities the gateway has permanently refused in this process.
     *
     * A `400` is deterministic for a given (kind, logical_key): re-sending it every tick would be an
     * infinite request loop that also re-poisons every batch it travels in. Bounded and drop-oldest,
     * because a set that grows with app behaviour is not a set, it is a leak.
     */
    private val poisoned = LinkedHashSet<RemoteConfigTelemetryEventIdentity>()

    private var boundScope: RemoteConfigSnapshotScope? = null
    private var generation = 0L
    private var inFlight = false
    private var attempt = 0
    private var retryScheduled = false
    private var retryTask: RemoteConfigFetchScheduledTask? = null
    private var tickTask: RemoteConfigFetchScheduledTask? = null

    /**
     * The identity whose retry ladder this process already exhausted.
     *
     * In memory on purpose: the bound is per process, so a rebind must not buy the same buffer
     * another three attempts against a gateway that is failing. A genuinely new process reads the
     * still-buffered events and tries once more.
     */
    private var abandonedScope: RemoteConfigSnapshotScope? = null

    /**
     * What the durable record of a scope is known to hold, keyed by that scope.
     *
     * Per scope rather than globally because the stamp only orders writes that address the SAME
     * preference key: one identity's newer write must not be able to suppress another identity's
     * older-but-unwritten one. `events == null` means "a write for this stamp was attempted and
     * failed", which forces the next identical buffer to try again instead of being skipped as
     * clean. Guarded by [storeLock].
     */
    private val committed = LinkedHashMap<RemoteConfigSnapshotScope, CommittedRecord>()

    /** Events abandoned without delivery, ever. Deliberately a counter and not a log. */
    val droppedEventCount: Long get() = dropped.get()

    /** How many distinct buckets are currently held. Diagnostics only. */
    internal val pendingEntryCount: Int get() = synchronized(lock) { entries.size }

    /**
     * Binds the sender to [scope] and resumes whatever that identity still owes.
     *
     * Binding fences every in-flight and scheduled attempt of the previous scope, and replaces the
     * in-memory buffer with the durable one: an event produced under one identity must never be
     * reported under another identity's session.
     */
    fun bind(scope: RemoteConfigSnapshotScope?) {
        if (synchronized(lock) { scope == boundScope }) return
        // Storage is read OUTSIDE the lock, deliberately: a config read takes this same lock, and
        // loading plus Moshi-parsing a record of up to 64 KiB would park that read behind an
        // identity change for as long as the disk takes.
        val persisted = scope?.let { loadEvents(it) }.orEmpty()
        rememberBaseline(scope, persisted)
        val resumed = synchronized(lock) {
            // Re-checked under the lock: the load raced whatever else may have bound meanwhile.
            if (scope == boundScope) return
            invalidateLocked()
            cancelTickLocked()
            boundScope = scope
            entries.clear()
            // Folded rather than assigned: a buffer written by an older build could hold two rows
            // for one (kind, key), and putting them in a map would silently lose a count.
            persisted.forEach(::mergeLocked)
            // Whatever happened before any identity existed belongs to the first one that does.
            replayPreBindLocked()
            if (entries.isNotEmpty()) armTickLocked()
            entries.isNotEmpty()
        }
        if (resumed) scheduleDrain(flush = true)
    }

    /**
     * Records what storage is known to hold for [scope], so an unchanged buffer is never rewritten.
     *
     * Called before the bind installs the loaded events: everything the sender persists afterwards
     * is compared against this, and a resume that changes nothing costs no disk write at all.
     */
    private fun rememberBaseline(scope: RemoteConfigSnapshotScope?, events: List<RemoteConfigTelemetryEvent>) {
        if (scope == null) return
        synchronized(storeLock) { rememberLocked(scope, writeStamp.incrementAndGet(), events) }
    }

    /** Folds one event into the coalescing map, honouring the bound and the poison set. */
    private fun mergeLocked(event: RemoteConfigTelemetryEvent) {
        if (event.identity in poisoned) {
            dropped.addAndGet(event.count)
            return
        }
        val existing = entries[event.identity]
        if (existing == null && entries.size >= maxEntries) {
            dropped.addAndGet(event.count)
            return
        }
        entries[event.identity] = existing?.coalescedWith(event) ?: event
    }

    private fun replayPreBindLocked() {
        val buffered = preBind.toList()
        preBind.clear()
        buffered.forEach(::mergeLocked)
    }

    /**
     * Records one read-guard event.
     *
     * Called from the read path (and from the preloader completion): it only touches an in-memory
     * map and returns. Nothing is persisted or sent on the caller's thread.
     */
    fun record(event: RemoteConfigReadGuardEvent) {
        // Not every guard event is a defect: see [toTelemetryKind].
        val kind = event.toTelemetryKind() ?: return
        enqueue(kind, logicalKey = "", releaseNumber = 0)
    }

    /**
     * Records that a typed decode of [logicalKey] failed while serving release [releaseNumber].
     *
     * Produced from the snapshot read site itself, which is why it may do nothing but enqueue: the
     * resolution ladder keeps walking and the value the caller receives is unaffected.
     */
    fun recordDecodeFailure(logicalKey: String, releaseNumber: Long) =
        enqueue(RemoteConfigTelemetryKind.DecodeFailure, logicalKey, releaseNumber)

    /**
     * Records that the fetch policy bookkeeping could not be persisted.
     *
     * Folded into `activation_persistence_failed` because the closed wire enum has exactly one
     * "the SDK could not persist its state" kind, and that is the signal the dashboard acts on.
     */
    fun recordPolicyPersistenceFailure() =
        enqueue(RemoteConfigTelemetryKind.ActivationPersistenceFailed, logicalKey = "", releaseNumber = 0)

    /**
     * Opportunistic flush after a fetch the gateway answered.
     *
     * The connection is warm and the session is known-good, so this is the cheapest moment to
     * deliver whatever has accumulated.
     */
    fun onSuccessfulFetch() {
        if (pendingEntryCount == 0) return
        scheduleDrain(flush = true)
    }

    @Suppress("ReturnCount")
    private fun enqueue(kind: RemoteConfigTelemetryKind, logicalKey: String, releaseNumber: Long) {
        var isNewEntry = false
        val flushDue = synchronized(lock) {
            val occurrence = RemoteConfigTelemetryEvent(
                kind = kind,
                logicalKey = logicalKey,
                // The bucket carries the release of its MOST RECENT occurrence, which is the same
                // rule the server applies when it merges an incoming event into a stored row.
                releaseNumber = releaseNumber.coerceAtLeast(0),
                count = 1,
                lastOccurredAtSeconds = nowSeconds(),
            )
            // Unsendable by contract, or already refused for good: queueing either would cost the
            // whole batch a terminal 400 — the second one on every tick, forever.
            if (!occurrence.isValid() || occurrence.identity in poisoned) {
                dropped.incrementAndGet()
                return
            }
            // Unbound means un-addressable: there is no session to report under. The event is held
            // in a bounded ring instead of thrown away, and belongs to whichever identity binds
            // first — a guard event is produced once per process and has no second chance.
            if (boundScope == null) {
                bufferBeforeBindLocked(occurrence)
                return
            }
            val existing = entries[occurrence.identity]
            if (existing == null && entries.size >= maxEntries) {
                dropped.incrementAndGet()
                return
            }
            entries[occurrence.identity] = existing?.coalescedWith(occurrence) ?: occurrence
            isNewEntry = existing == null
            armTickLocked()
            // LATCHED on a new bucket. Testing only `size >= flushThreshold` would make every
            // counter-only bump past the tenth bucket schedule a drain — and therefore a
            // synchronous preferences commit — on the worker the config path shares.
            isNewEntry && entries.size >= flushThreshold
        }
        if (isNewEntry) scheduleDrain(flush = flushDue)
    }

    private fun bufferBeforeBindLocked(event: RemoteConfigTelemetryEvent) {
        while (preBind.size >= maxPreBindEvents) {
            // Drop-oldest: the newest evidence of a still-broken key is worth more than the oldest.
            dropped.addAndGet(preBind.removeFirst().count)
        }
        preBind.addLast(event)
    }

    /**
     * Hands the durable write — and, when one is due, the flush — to [executor].
     *
     * Coalesced through [drainScheduled] so a burst of reads that each produce an event still costs
     * at most one queued task.
     */
    private fun scheduleDrain(flush: Boolean) {
        if (flush) flushRequested.set(true)
        if (!drainScheduled.compareAndSet(false, true)) return
        val submitted = try {
            executor.execute(::drain)
            true
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            false
        }
        // A shut-down worker simply means this drain is not taken; the buffer stays in memory and
        // the next record (or the next process) persists it.
        if (!submitted) drainScheduled.set(false)
    }

    private fun drain() {
        drainScheduled.set(false)
        persistCurrentBuffer()
        if (flushRequested.getAndSet(false)) startIfIdle()
    }

    private fun persistCurrentBuffer() {
        val write = synchronized(lock) { prepareWriteLocked() }
        commit(write)
    }

    private fun startIfIdle() {
        val outcome = synchronized(lock) { claimAttemptLocked() }
        // The prune is committed BEFORE the batch goes out: an all-stale record that is only pruned
        // in memory comes back on the next start, is pruned again, and inflates the drop counter
        // once per process for the rest of the installation's life.
        commit(outcome.write)
        outcome.attempt?.let(::dispatch)
    }

    @Suppress("ReturnCount")
    private fun claimAttemptLocked(): AttemptOutcome {
        val scope = boundScope ?: return AttemptOutcome()
        if (inFlight || retryScheduled) return AttemptOutcome()
        if (scope == abandonedScope) return AttemptOutcome()
        val draft = prepareBatchLocked()
        val batch = draft.batch ?: return AttemptOutcome(write = draft.write)
        inFlight = true
        attempt = 1
        return AttemptOutcome(Attempt(generation, scope, batch), draft.write)
    }

    /**
     * Builds the next batch, dropping anything the gateway would refuse on sight.
     *
     * The pruning is the point: a `400` is terminal for the WHOLE batch, so a single event whose
     * timestamp fell out of the server's window — a buffer that survived a month of offline starts,
     * or a device whose clock jumped forward — would take every healthy event with it. What it
     * removes is handed back as a durable write, because a prune that only happens in memory is a
     * record that never dies.
     */
    private fun prepareBatchLocked(): BatchDraft {
        val now = nowSeconds()
        if (now < REMOTE_CONFIG_TELEMETRY_CLOCK_FLOOR_SECONDS) {
            // An unset RTC would classify EVERY buffered event as future-skewed and throw the whole
            // buffer away. A clock we cannot believe suspends both the prune and the flush; the
            // tick keeps asking until it becomes real.
            if (entries.isNotEmpty()) armTickLocked()
            return BatchDraft()
        }
        var prunedOccurrences = 0L
        entries.entries.removeAll { (_, event) ->
            val stale = event.lastOccurredAtSeconds < now - REMOTE_CONFIG_TELEMETRY_MAX_AGE_SECONDS ||
                event.lastOccurredAtSeconds > now + REMOTE_CONFIG_TELEMETRY_MAX_SKEW_SECONDS
            if (stale) prunedOccurrences += event.count
            stale
        }
        if (prunedOccurrences > 0) dropped.addAndGet(prunedOccurrences)
        if (entries.isEmpty()) cancelTickLocked()
        return BatchDraft(
            batch = entries.values.take(maxBatchEvents).takeIf { it.isNotEmpty() },
            write = if (prunedOccurrences > 0) prepareWriteLocked() else null,
        )
    }

    private class BatchDraft(
        val batch: List<RemoteConfigTelemetryEvent>? = null,
        val write: PendingWrite? = null,
    )

    private class AttemptOutcome(
        val attempt: Attempt? = null,
        val write: PendingWrite? = null,
    )

    private fun dispatch(sending: Attempt) {
        try {
            transport.postTelemetry(sending.scope, sending.events) { response -> onResponse(sending, response) }
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            onResponse(sending, RemoteConfigAckResponse.Retryable)
        }
    }

    @Suppress("ReturnCount")
    private fun onResponse(sent: Attempt, response: RemoteConfigAckResponse) {
        if (!sent.claim()) return
        var retryDelayMillis: Long? = null
        var hasMore = false
        val write = synchronized(lock) {
            // A bind happened while this batch was on the wire: its answer says nothing about the
            // identity the sender addresses now.
            if (sent.generation != generation || sent.scope != boundScope) return
            inFlight = false
            val write = when (response) {
                RemoteConfigAckResponse.Delivered -> settleLocked(sent)
                // A terminal 400 family answer: the batch can only ever be refused again.
                RemoteConfigAckResponse.Permanent -> poisonLocked(sent)
                // Not an attempt: the retry budget is untouched and the buffer stays queued.
                RemoteConfigAckResponse.NotAddressable -> null
                RemoteConfigAckResponse.Retryable -> if (attempt >= maxAttempts) {
                    // Owed but abandoned for this process; the durable buffer is left untouched so
                    // the next process start delivers it.
                    abandonedScope = sent.scope
                    null
                } else {
                    retryDelayMillis = retryDelayLocked(attempt)
                    null
                }
            }
            retryDelayMillis?.let { scheduleRetryLocked(it) }
            hasMore = write != null && entries.isNotEmpty()
            write
        }
        commit(write)
        // The map is bounded at 64 and a batch carries 50, so this recurses at most once.
        if (hasMore) startIfIdle()
    }

    /**
     * Removes exactly what the gateway answered for.
     *
     * Occurrences that arrived WHILE the batch was on the wire are kept: the bucket is decremented
     * by the reported count instead of being cleared, so a decode failure that keeps happening is
     * still reported by the next batch.
     */
    private fun settleLocked(sent: Attempt): PendingWrite? {
        sent.events.forEach { event ->
            val current = entries[event.identity] ?: return@forEach
            if (current.count <= event.count) {
                entries.remove(event.identity)
            } else {
                entries[event.identity] = current.copy(count = current.count - event.count)
            }
        }
        if (entries.isEmpty()) cancelTickLocked()
        return prepareWriteLocked()
    }

    /**
     * Retires a permanently refused batch, and everything that shares its identities.
     *
     * The bucket is removed WHOLE — including occurrences that accrued while the batch was on the
     * wire — and its identity is remembered as poisoned. Decrementing instead would leave a residue
     * that re-flushes on the very next tick and is refused again, forever: a `400` is deterministic
     * for a given (kind, logical_key), so the only way to stop asking is to stop asking.
     */
    private fun poisonLocked(sent: Attempt): PendingWrite? {
        sent.events.forEach { event ->
            rememberPoisonLocked(event.identity)
            val current = entries.remove(event.identity)
            dropped.addAndGet(current?.count ?: event.count)
        }
        if (entries.isEmpty()) cancelTickLocked()
        return prepareWriteLocked()
    }

    private fun rememberPoisonLocked(identity: RemoteConfigTelemetryEventIdentity) {
        // Bounded and drop-oldest: a set that grows with app behaviour is not a set, it is a leak.
        while (poisoned.size >= maxEntries) {
            poisoned.remove(poisoned.first())
        }
        poisoned += identity
    }

    private fun scheduleRetryLocked(delayMillis: Long) {
        val scheduledGeneration = generation
        retryScheduled = true
        retryTask = try {
            scheduler.schedule(delayMillis) { onRetryDue(scheduledGeneration) }
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            retryScheduled = false
            null
        }
    }

    private fun onRetryDue(scheduledGeneration: Long) {
        val outcome = synchronized(lock) { claimRetryLocked(scheduledGeneration) }
        commit(outcome.write)
        outcome.attempt?.let(::dispatch)
    }

    @Suppress("ReturnCount")
    private fun claimRetryLocked(scheduledGeneration: Long): AttemptOutcome {
        if (scheduledGeneration != generation) return AttemptOutcome()
        retryScheduled = false
        retryTask = null
        val scope = boundScope ?: return AttemptOutcome()
        if (inFlight) return AttemptOutcome()
        val draft = prepareBatchLocked()
        val batch = draft.batch ?: return AttemptOutcome(write = draft.write)
        attempt += 1
        inFlight = true
        return AttemptOutcome(Attempt(generation, scope, batch), draft.write)
    }

    /**
     * Arms the periodic flush, but only while something is actually buffered: an idle SDK must not
     * wake a thread every 30 seconds for an empty batch.
     */
    private fun armTickLocked() {
        if (tickTask != null) return
        val scheduledGeneration = generation
        tickTask = try {
            scheduler.schedule(tickIntervalMillis) { onTickDue(scheduledGeneration) }
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            null
        }
    }

    private fun onTickDue(scheduledGeneration: Long) {
        val due = synchronized(lock) {
            if (scheduledGeneration != generation) return
            tickTask = null
            if (entries.isEmpty()) return
            armTickLocked()
            true
        }
        if (due) scheduleDrain(flush = true)
    }

    private fun cancelTickLocked() {
        try {
            tickTask?.cancel()
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            // Generation fencing, not cancellation, is what makes a stale timer harmless.
        }
        tickTask = null
    }

    /**
     * Fences everything in flight or scheduled.
     *
     * Only the in-memory delivery is invalidated; the durable buffer is untouched, because the
     * events it holds are still owed by the identity that produced them.
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

    private fun prepareWriteLocked(): PendingWrite? {
        val scope = boundScope ?: return null
        return PendingWrite(scope, entries.values.toList(), writeStamp.incrementAndGet())
    }

    /**
     * Writes a prepared buffer, outside [lock] so a synchronous disk commit can never park a thread
     * that is reading a config value.
     *
     * Two guards, both load-bearing:
     * - the per-scope stamp keeps concurrent writers from committing out of order — a write prepared
     *   before the last committed one for the SAME scope is dropped rather than allowed to
     *   resurrect it;
     * - the dirty check drops a write whose content the record already holds. That is what makes
     *   "storage is touched only when the buffer changed shape" true in practice, including on the
     *   drain that follows every flush trigger.
     */
    @Suppress("ReturnCount")
    private fun commit(write: PendingWrite?) {
        if (write == null) return
        synchronized(storeLock) {
            val previous = committed[write.scope]
            if (previous != null && write.stamp <= previous.stamp) return
            if (previous?.events == write.events) return
            val written = try {
                if (write.events.isEmpty()) store.clear(write.scope) else store.save(write.scope, write.events)
            } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
                // The in-memory buffer still governs this process; a lost write can at worst cost
                // duplicated counts after a restart, which the aggregate storage tolerates.
                false
            }
            // A failed write records the stamp but NOT the content, so the next identical buffer is
            // still considered dirty and tries again instead of being skipped as clean.
            rememberLocked(write.scope, write.stamp, write.events.takeIf { written })
        }
    }

    private fun rememberLocked(
        scope: RemoteConfigSnapshotScope,
        stamp: Long,
        events: List<RemoteConfigTelemetryEvent>?,
    ) {
        // Bounded: an app that identifies through many users must not accumulate one record per
        // identity it has ever seen.
        while (committed.size >= COMMITTED_HISTORY && !committed.containsKey(scope)) {
            committed.remove(committed.keys.first())
        }
        committed[scope] = CommittedRecord(stamp, events)
    }

    private class PendingWrite(
        val scope: RemoteConfigSnapshotScope,
        val events: List<RemoteConfigTelemetryEvent>,
        val stamp: Long,
    )

    private class CommittedRecord(
        val stamp: Long,
        val events: List<RemoteConfigTelemetryEvent>?,
    )

    private fun loadEvents(scope: RemoteConfigSnapshotScope): List<RemoteConfigTelemetryEvent> = try {
        store.load(scope).filter { it.isValid() }.take(maxEntries)
    } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
        emptyList()
    }

    /**
     * The occurrence timestamp, floored at 1: a zero is indistinguishable from "absent" in the
     * durable record and would make the buffered event silently un-persistable.
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
     * both calls back and throws must not advance the retry budget twice.
     */
    private class Attempt(
        val generation: Long,
        val scope: RemoteConfigSnapshotScope,
        val events: List<RemoteConfigTelemetryEvent>,
    ) {
        private val answered = AtomicBoolean(false)

        fun claim(): Boolean = answered.compareAndSet(false, true)
    }

    private companion object {
        /** How many identity scopes keep durable-write bookkeeping. */
        const val COMMITTED_HISTORY = 8
    }
}

/**
 * The read guard's vocabulary, translated into the closed wire enum — or into nothing.
 *
 * `PreloadNotReady` is deliberately NOT reported. It means the first read outran the preload, which
 * is the ordinary first-launch state of every fresh install rather than a defect; mapping it onto
 * `preload_failed` would make the one metric the dashboard alarms on fire for every install. Only a
 * genuine failure to read persisted state — `PreloadFailed` — is a failure.
 */
internal fun RemoteConfigReadGuardEvent.toTelemetryKind(): RemoteConfigTelemetryKind? = when (this) {
    RemoteConfigReadGuardEvent.ReadBeforeActivate -> RemoteConfigTelemetryKind.ReadBeforeActivate
    RemoteConfigReadGuardEvent.ImplicitActivation -> RemoteConfigTelemetryKind.ImplicitActivation
    RemoteConfigReadGuardEvent.PreloadNotReady -> null
    RemoteConfigReadGuardEvent.PreloadFailed -> RemoteConfigTelemetryKind.PreloadFailed
    RemoteConfigReadGuardEvent.PreloadCorrupt -> RemoteConfigTelemetryKind.PreloadCorrupt
    RemoteConfigReadGuardEvent.ActivationPersistenceFailed -> RemoteConfigTelemetryKind.ActivationPersistenceFailed
}

/**
 * Durable, per-identity-scope telemetry buffer.
 *
 * Mirrors [PersistentRemoteConfigActivationAckStore]: the storage key is a salted digest of the
 * scope, so neither the project key nor the canonical user id ever lands in a preference name.
 */
internal class PersistentRemoteConfigTelemetryStore(
    private val cache: Cache,
    moshi: Moshi,
    private val maxBytes: Int = REMOTE_CONFIG_TELEMETRY_MAX_BYTES,
    private val maxEntries: Int = REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES,
) : RemoteConfigTelemetryStore {
    private val adapter = moshi.adapter(PersistedRemoteConfigTelemetry::class.java)

    @Synchronized
    @Suppress("ReturnCount")
    override fun load(scope: RemoteConfigSnapshotScope): List<RemoteConfigTelemetryEvent> {
        val storageKey = remoteConfigTelemetryStorageKey(scope)
        val raw = try {
            cache.getString(storageKey, null)
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val persisted = try {
            raw.takeIf { it.toByteArray(Charsets.UTF_8).size <= maxBytes }
                ?.let(adapter::fromJson)
        } catch (_: Exception) {
            null
        }
        if (persisted == null || persisted.version != REMOTE_CONFIG_TELEMETRY_VERSION) {
            removeInvalid(storageKey)
            return emptyList()
        }
        return persisted.events.mapNotNull { it.toEvent() }.take(maxEntries)
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun save(scope: RemoteConfigSnapshotScope, events: List<RemoteConfigTelemetryEvent>): Boolean {
        // The byte budget is enforced by shrinking the batch rather than by refusing the write: a
        // partial buffer is strictly better telemetry than none, and the oldest buckets are the
        // ones the next flush would have sent first anyway.
        var candidates = events.filter { it.isValid() }.take(maxEntries)
        while (candidates.isNotEmpty()) {
            val raw = encode(candidates) ?: return false
            if (raw.toByteArray(Charsets.UTF_8).size <= maxBytes) {
                return writeDurably(scope, raw)
            }
            candidates = candidates.dropLast(1)
        }
        return clear(scope)
    }

    @Synchronized
    override fun clear(scope: RemoteConfigSnapshotScope): Boolean = try {
        cache.updateStringsDurably(emptyMap(), setOf(remoteConfigTelemetryStorageKey(scope)))
    } catch (_: Exception) {
        false
    }

    private fun encode(events: List<RemoteConfigTelemetryEvent>): String? = try {
        adapter.toJson(
            PersistedRemoteConfigTelemetry(
                version = REMOTE_CONFIG_TELEMETRY_VERSION,
                events = events.map { it.toPersisted() },
            ),
        )
    } catch (_: Exception) {
        null
    }

    private fun writeDurably(scope: RemoteConfigSnapshotScope, raw: String): Boolean = try {
        cache.updateStringsDurably(
            values = mapOf(remoteConfigTelemetryStorageKey(scope) to raw),
            removedKeys = emptySet(),
        )
    } catch (_: Exception) {
        false
    }

    private fun removeInvalid(key: String) {
        try {
            cache.updateStringsDurably(emptyMap(), setOf(key))
        } catch (_: Exception) {
            // A malformed record stays untrusted even when best-effort cleanup fails.
        }
    }

    private fun RemoteConfigTelemetryEvent.toPersisted() = PersistedRemoteConfigTelemetryEvent(
        kind = kind.wireName,
        logicalKey = logicalKey,
        releaseNumber = releaseNumber,
        count = count,
        lastOccurredAt = lastOccurredAtSeconds,
    )

    private fun PersistedRemoteConfigTelemetryEvent.toEvent(): RemoteConfigTelemetryEvent? {
        val parsedKind = RemoteConfigTelemetryKind.fromWireName(kind) ?: return null
        return RemoteConfigTelemetryEvent(
            kind = parsedKind,
            logicalKey = logicalKey,
            releaseNumber = releaseNumber,
            count = count,
            lastOccurredAtSeconds = lastOccurredAt,
        ).takeIf { it.isValid() }
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigTelemetry(
    val version: Int,
    @Json(name = "events")
    val events: List<PersistedRemoteConfigTelemetryEvent>,
)

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigTelemetryEvent(
    @Json(name = "kind")
    val kind: String,
    @Json(name = "logical_key")
    val logicalKey: String,
    @Json(name = "release_number")
    val releaseNumber: Long,
    @Json(name = "count")
    val count: Long,
    @Json(name = "last_occurred_at")
    val lastOccurredAt: Long,
)

private fun remoteConfigTelemetryStorageKey(scope: RemoteConfigSnapshotScope): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateLengthPrefixed("remote-config-telemetry-v1".encodeToByteArray())
    digest.updateLengthPrefixed(scope.projectKey.encodeToByteArray())
    digest.updateLengthPrefixed(scope.environment.encodeToByteArray())
    digest.updateLengthPrefixed(scope.canonicalUserId.encodeToByteArray())
    return REMOTE_CONFIG_TELEMETRY_PREFIX + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun MessageDigest.updateLengthPrefixed(value: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
    update(value)
}
