package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.internal.InternalConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

private const val DEFAULT_MAX_REMOTE_CONFIG_CACHE_BYTES = 512 * 1024
private const val MAX_REMOTE_CONFIG_INDEX_BYTES = 64 * 1024

private fun String?.normalizedRemoteConfigContextKey(): String? = takeUnless { it.isNullOrEmpty() }

internal data class RemoteConfigCacheScope(
    val projectKey: String,
    val environment: String,
    val userId: String,
)

internal data class RemoteConfigCacheLimits(
    val maxScopes: Int = 8,
    val maxEntriesPerScope: Int = 64,
    val maxTotalBytes: Int = DEFAULT_MAX_REMOTE_CONFIG_CACHE_BYTES,
) {
    init {
        require(maxScopes > 0)
        require(maxEntriesPerScope > 0)
        require(maxTotalBytes > 0)
    }
}

internal interface RemoteConfigCache {
    fun currentScope(): RemoteConfigCacheScope? = null
    fun save(remoteConfig: QRemoteConfig)
    fun save(scope: RemoteConfigCacheScope, remoteConfig: QRemoteConfig) = save(remoteConfig)
    fun save(
        scope: RemoteConfigCacheScope,
        remoteConfig: QRemoteConfig,
        completion: (Boolean) -> Unit,
    )
    fun remove(contextKey: String?)
    fun remove(scope: RemoteConfigCacheScope, contextKey: String?) = remove(contextKey)
    fun remove(
        scope: RemoteConfigCacheScope,
        contextKey: String?,
        completion: (Boolean) -> Unit,
    )
    fun replaceAll(remoteConfigs: List<QRemoteConfig>)
    fun replaceAll(scope: RemoteConfigCacheScope, remoteConfigs: List<QRemoteConfig>) = replaceAll(remoteConfigs)
    fun replaceAll(
        scope: RemoteConfigCacheScope,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit,
    )
    fun replaceRequested(requestedContextKeys: Set<String?>, remoteConfigs: List<QRemoteConfig>)
    fun replaceRequested(
        scope: RemoteConfigCacheScope,
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
    ) = replaceRequested(requestedContextKeys, remoteConfigs)
    fun replaceRequested(
        scope: RemoteConfigCacheScope,
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit,
    )
    fun get(contextKey: String?): QRemoteConfig?
    fun get(scope: RemoteConfigCacheScope, contextKey: String?): QRemoteConfig? = get(contextKey)
    fun getAll(): QRemoteConfigList
    fun getAll(scope: RemoteConfigCacheScope): QRemoteConfigList = getAll()
}

internal class PersistentRemoteConfigCache(
    private val cache: Cache,
    private val config: InternalConfig,
    moshi: Moshi,
    private val limits: RemoteConfigCacheLimits = RemoteConfigCacheLimits(),
    private val persistenceExecutor: Executor = DEFAULT_PERSISTENCE_EXECUTOR,
) : RemoteConfigCache {
    private val adapter = moshi.adapter(PersistentRemoteConfigEnvelope::class.java)
    private val remoteConfigAdapter = moshi.adapter(QRemoteConfig::class.java)
    private val indexAdapter = moshi.adapter(PersistentRemoteConfigIndex::class.java)
    private val memoryEnvelopes = mutableMapOf<String, PersistentRemoteConfigEnvelope>()
    private val pendingRevisions = mutableMapOf<String, Long>()
    private val pendingEnvelopes = mutableMapOf<String, PersistentRemoteConfigEnvelope?>()
    private val pendingCompletions = mutableMapOf<String, MutableList<PendingCompletion>>()
    private var nextRevision = 0L

    @Synchronized
    override fun save(remoteConfig: QRemoteConfig) {
        val scope = currentScope() ?: return
        save(scope, remoteConfig)
    }

    @Synchronized
    override fun save(scope: RemoteConfigCacheScope, remoteConfig: QRemoteConfig) {
        save(scope, remoteConfig, requireExactPersistence = false) {}
    }

    @Synchronized
    override fun save(
        scope: RemoteConfigCacheScope,
        remoteConfig: QRemoteConfig,
        completion: (Boolean) -> Unit,
    ) = save(scope, remoteConfig, requireExactPersistence = false, completion)

    private fun save(
        scope: RemoteConfigCacheScope,
        remoteConfig: QRemoteConfig,
        requireExactPersistence: Boolean,
        completion: (Boolean) -> Unit,
    ) {
        if (!remoteConfig.isCorrect) {
            completion(false)
            return
        }

        val currentConfigs = loadLatestEnvelope(scope)?.remoteConfigs.orEmpty()
        val contextKey = remoteConfig.source.contextKey.normalizedRemoteConfigContextKey()
        val updatedConfigs = currentConfigs
            .filterNot { it.source.contextKey.normalizedRemoteConfigContextKey() == contextKey }
            .plus(remoteConfig)
            .takeLast(limits.maxEntriesPerScope)
        scheduleWrite(
            scope,
            updatedConfigs,
            completion,
            requireExactPersistence,
        ) { committedEnvelope ->
            committedEnvelope?.remoteConfigs?.any { it == remoteConfig } == true
        }
    }

    @Synchronized
    override fun remove(contextKey: String?) {
        val scope = currentScope() ?: return
        remove(scope, contextKey)
    }

    @Synchronized
    override fun remove(scope: RemoteConfigCacheScope, contextKey: String?) {
        remove(scope, contextKey, requireExactPersistence = false) {}
    }

    @Synchronized
    override fun remove(
        scope: RemoteConfigCacheScope,
        contextKey: String?,
        completion: (Boolean) -> Unit,
    ) = remove(scope, contextKey, requireExactPersistence = true, completion)

    private fun remove(
        scope: RemoteConfigCacheScope,
        contextKey: String?,
        requireExactPersistence: Boolean,
        completion: (Boolean) -> Unit,
    ) {
        val normalizedContextKey = contextKey.normalizedRemoteConfigContextKey()
        val updatedConfigs = loadLatestEnvelope(scope)?.remoteConfigs.orEmpty()
            .filterNot { it.source.contextKey.normalizedRemoteConfigContextKey() == normalizedContextKey }
        scheduleWrite(
            scope,
            updatedConfigs,
            completion,
            requireExactPersistence,
        ) { committedEnvelope ->
            committedEnvelope?.remoteConfigs.orEmpty().none {
                it.source.contextKey.normalizedRemoteConfigContextKey() == normalizedContextKey
            }
        }
    }

    @Synchronized
    override fun replaceAll(remoteConfigs: List<QRemoteConfig>) {
        val scope = currentScope() ?: return
        replaceAll(scope, remoteConfigs)
    }

    @Synchronized
    override fun replaceAll(scope: RemoteConfigCacheScope, remoteConfigs: List<QRemoteConfig>) {
        replaceAll(scope, remoteConfigs, requireExactPersistence = false) {}
    }

    @Synchronized
    override fun replaceAll(
        scope: RemoteConfigCacheScope,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit,
    ) = replaceAll(scope, remoteConfigs, requireExactPersistence = true, completion)

    private fun replaceAll(
        scope: RemoteConfigCacheScope,
        remoteConfigs: List<QRemoteConfig>,
        requireExactPersistence: Boolean,
        completion: (Boolean) -> Unit,
    ) {
        if (!remoteConfigs.areValidForPersistence()) {
            completion(false)
            return
        }
        if (requireExactPersistence && remoteConfigs.size > limits.maxEntriesPerScope) {
            completion(false)
            return
        }

        scheduleWrite(
            scope,
            remoteConfigs.takeLast(limits.maxEntriesPerScope),
            completion,
            requireExactPersistence,
        )
    }

    @Synchronized
    override fun replaceRequested(
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
    ) {
        val scope = currentScope() ?: return
        replaceRequested(scope, requestedContextKeys, remoteConfigs)
    }

    @Synchronized
    override fun replaceRequested(
        scope: RemoteConfigCacheScope,
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
    ) {
        replaceRequested(
            scope,
            requestedContextKeys,
            remoteConfigs,
            requireExactPersistence = false,
        ) {}
    }

    @Synchronized
    override fun replaceRequested(
        scope: RemoteConfigCacheScope,
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit,
    ) = replaceRequested(
        scope,
        requestedContextKeys,
        remoteConfigs,
        requireExactPersistence = true,
        completion,
    )

    private fun replaceRequested(
        scope: RemoteConfigCacheScope,
        requestedContextKeys: Set<String?>,
        remoteConfigs: List<QRemoteConfig>,
        requireExactPersistence: Boolean,
        completion: (Boolean) -> Unit,
    ) {
        val normalizedRequestedKeys = requestedContextKeys
            .mapTo(mutableSetOf()) { it.normalizedRemoteConfigContextKey() }
        if (!remoteConfigs.areValidForRequestedPersistence(normalizedRequestedKeys)) {
            completion(false)
            return
        }

        val requestedUpdate = loadLatestEnvelope(scope)?.remoteConfigs.orEmpty()
            .filterNot { config ->
                config.source.contextKey.normalizedRemoteConfigContextKey() in normalizedRequestedKeys
            }
            .plus(remoteConfigs)
        if (requireExactPersistence && requestedUpdate.size > limits.maxEntriesPerScope) {
            completion(false)
            return
        }
        val updatedConfigs = requestedUpdate.takeLast(limits.maxEntriesPerScope)
        val expectedConfigs = remoteConfigs.associateBy {
            it.source.contextKey.normalizedRemoteConfigContextKey()
        }
        val omittedContextKeys = normalizedRequestedKeys - expectedConfigs.keys
        scheduleWrite(
            scope,
            updatedConfigs,
            completion,
            requireExactPersistence,
        ) { committedEnvelope ->
            val committedConfigs = committedEnvelope?.remoteConfigs.orEmpty().associateBy {
                it.source.contextKey.normalizedRemoteConfigContextKey()
            }
            expectedConfigs.all { (contextKey, expected) -> committedConfigs[contextKey] == expected } &&
                omittedContextKeys.none { it in committedConfigs }
        }
    }

    @Synchronized
    override fun get(contextKey: String?): QRemoteConfig? {
        val scope = currentScope() ?: return null
        return get(scope, contextKey)
    }

    @Synchronized
    override fun get(scope: RemoteConfigCacheScope, contextKey: String?): QRemoteConfig? {
        val envelope = loadEnvelope(scope) ?: return null
        val normalizedContextKey = contextKey.normalizedRemoteConfigContextKey()
        val remoteConfig = envelope.remoteConfigs.firstOrNull {
            it.source.contextKey.normalizedRemoteConfigContextKey() == normalizedContextKey
        }
        remoteConfig?.let { accessed ->
            if (!pendingEnvelopes.containsKey(scope.storageKey)) {
                scheduleWrite(
                    scope,
                    envelope.remoteConfigs.filterNot {
                        it.source.contextKey.normalizedRemoteConfigContextKey() == normalizedContextKey
                    } + accessed,
                )
            }
        }
        return remoteConfig
    }

    @Synchronized
    override fun getAll(): QRemoteConfigList {
        val scope = currentScope() ?: return QRemoteConfigList(emptyList())
        return getAll(scope)
    }

    @Synchronized
    override fun getAll(scope: RemoteConfigCacheScope): QRemoteConfigList {
        val remoteConfigs = loadEnvelope(scope)?.remoteConfigs.orEmpty()
        if (remoteConfigs.isNotEmpty() && !pendingEnvelopes.containsKey(scope.storageKey)) {
            scheduleWrite(scope, remoteConfigs)
        }
        return QRemoteConfigList(remoteConfigs)
    }

    private fun scheduleWrite(
        scope: RemoteConfigCacheScope,
        remoteConfigs: List<QRemoteConfig>,
        completion: (Boolean) -> Unit = {},
        requireExactPersistence: Boolean = false,
        isSuccessfulCommit: ((PersistentRemoteConfigEnvelope?) -> Boolean)? = null,
    ) {
        val storageKey = scope.storageKey
        val envelope = remoteConfigs.takeIf { it.isNotEmpty() }?.let {
            PersistentRemoteConfigEnvelope(
                version = CACHE_VERSION,
                projectKey = scope.projectKey,
                environment = scope.environment,
                userId = scope.userId,
                remoteConfigs = it,
            )
        }
        // Admission must finish before publishing a new pending revision: otherwise an
        // unpersistable newer mutation can cancel an already accepted write. This bounded
        // serialization runs on the caller; production evaluates at most 64 entries and
        // admits at most 512 KiB (an oversized entry is serialized once to reject it), while
        // the durable SharedPreferences commit remains on persistenceExecutor.
        val persistencePayload = preparePersistencePayload(envelope, requireExactPersistence)
        if (persistencePayload == null) {
            completion(false)
            return
        }

        val previousPendingState = PendingState(
            revision = pendingRevisions[storageKey],
            hasEnvelope = pendingEnvelopes.containsKey(storageKey),
            envelope = pendingEnvelopes[storageKey],
            completions = pendingCompletions[storageKey],
        )
        val revision = ++nextRevision
        pendingRevisions[storageKey] = revision
        // Subsequent coalesced mutations must build from what can actually become durable,
        // not from entries removed by byte-bound admission.
        pendingEnvelopes[storageKey] = persistencePayload.envelope
        pendingCompletions[storageKey] = previousPendingState.completions.orEmpty().toMutableList().apply {
            add(PendingCompletion(isSuccessfulCommit ?: { committed -> committed == envelope }, completion))
        }
        try {
            persistenceExecutor.execute {
                persistLatest(storageKey, revision, persistencePayload)
            }
        } catch (_: RejectedExecutionException) {
            if (pendingRevisions[storageKey] == revision) {
                restorePendingState(storageKey, previousPendingState)
                completion(false)
            }
        }
    }

    private fun persistLatest(
        storageKey: String,
        revision: Long,
        persistencePayload: PersistencePayload,
    ) {
        val completionResults = commitLatestPersistence(storageKey, revision, persistencePayload)
        completionResults.forEach { (completion, committed) -> completion(committed) }
    }

    private fun restorePendingState(storageKey: String, previous: PendingState) {
        previous.revision?.let { pendingRevisions[storageKey] = it }
            ?: pendingRevisions.remove(storageKey)
        if (previous.hasEnvelope) {
            pendingEnvelopes[storageKey] = previous.envelope
        } else {
            pendingEnvelopes.remove(storageKey)
        }
        previous.completions?.let { pendingCompletions[storageKey] = it }
            ?: pendingCompletions.remove(storageKey)
    }

    private fun preparePersistencePayload(
        envelope: PersistentRemoteConfigEnvelope?,
        requireExactPersistence: Boolean,
    ): PersistencePayload? = try {
        if (envelope == null) {
            PersistencePayload(null, null)
        } else {
            envelope.let(::fitWithinByteLimit)
                ?.takeUnless { (boundedEnvelope) ->
                    requireExactPersistence && boundedEnvelope != envelope
                }
                ?.let { (boundedEnvelope, json) -> PersistencePayload(boundedEnvelope, json) }
        }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    private fun commitLatestPersistence(
        storageKey: String,
        revision: Long,
        persistencePayload: PersistencePayload?,
    ): List<Pair<(Boolean) -> Unit, Boolean>> {
        if (pendingRevisions[storageKey] != revision) return emptyList()

        val attempt = persistPayload(storageKey, persistencePayload)
        if (attempt.committed) {
            updateCommittedMemory(storageKey, persistencePayload, attempt.indexUpdate)
        }
        pendingRevisions.remove(storageKey)
        pendingEnvelopes.remove(storageKey)
        return pendingCompletions.remove(storageKey).orEmpty().map { pending ->
            pending.callback to (
                attempt.committed && pending.isSuccessfulCommit(persistencePayload?.envelope)
            )
        }
    }

    private fun persistPayload(
        storageKey: String,
        persistencePayload: PersistencePayload?,
    ): PersistenceAttempt {
        if (persistencePayload == null) return PersistenceAttempt.failed()

        return try {
            val json = persistencePayload.json
            val indexUpdate = createIndexUpdate(
                storageKey,
                json?.toByteArray(Charsets.UTF_8)?.size,
            )
            val values = buildMap<String, String?> {
                json?.let { put(storageKey, it) }
                indexUpdate.index?.let { put(CACHE_INDEX_KEY, indexAdapter.toJson(it)) }
            }
            val removedKeys = buildSet {
                if (json == null) add(storageKey)
                addAll(indexUpdate.evictedStorageKeys)
                if (indexUpdate.index == null) add(CACHE_INDEX_KEY)
            } - values.keys
            PersistenceAttempt(cache.updateStringsDurably(values, removedKeys), indexUpdate)
        } catch (_: Exception) {
            PersistenceAttempt.failed()
        }
    }

    private fun updateCommittedMemory(
        storageKey: String,
        persistencePayload: PersistencePayload?,
        indexUpdate: PersistentRemoteConfigIndexUpdate?,
    ) {
        persistencePayload?.envelope?.let { memoryEnvelopes[storageKey] = it }
            ?: memoryEnvelopes.remove(storageKey)
        indexUpdate?.evictedStorageKeys.orEmpty().forEach { evictedStorageKey ->
            if (!pendingRevisions.containsKey(evictedStorageKey)) {
                memoryEnvelopes.remove(evictedStorageKey)
            }
        }
    }

    @Synchronized
    private fun loadLatestEnvelope(scope: RemoteConfigCacheScope): PersistentRemoteConfigEnvelope? {
        val storageKey = scope.storageKey
        return if (pendingEnvelopes.containsKey(storageKey)) {
            pendingEnvelopes[storageKey]
        } else {
            loadEnvelope(scope)
        }
    }

    private fun fitWithinByteLimit(
        original: PersistentRemoteConfigEnvelope,
    ): Pair<PersistentRemoteConfigEnvelope, String>? {
        val emptyEnvelopeBytes = adapter.toJson(original.copy(remoteConfigs = emptyList()))
            .toByteArray(Charsets.UTF_8)
            .size
        var suffixStart = original.remoteConfigs.size
        var suffixEntriesBytes = 0
        for (index in original.remoteConfigs.lastIndex downTo 0) {
            val entryBytes = remoteConfigAdapter.toJson(original.remoteConfigs[index])
                .toByteArray(Charsets.UTF_8)
                .size
            val separatorBytes = if (suffixStart == original.remoteConfigs.size) 0 else 1
            if (emptyEnvelopeBytes + suffixEntriesBytes + separatorBytes + entryBytes > limits.maxTotalBytes) {
                break
            }
            suffixEntriesBytes += separatorBytes + entryBytes
            suffixStart = index
        }
        if (suffixStart == original.remoteConfigs.size) return null

        val boundedEnvelope = original.copy(
            remoteConfigs = original.remoteConfigs.subList(suffixStart, original.remoteConfigs.size),
        )
        val json = adapter.toJson(boundedEnvelope)
        return (boundedEnvelope to json).takeIf {
            json.toByteArray(Charsets.UTF_8).size <= limits.maxTotalBytes
        }
    }

    private fun createIndexUpdate(storageKey: String, bytes: Int?): PersistentRemoteConfigIndexUpdate {
        val existing = loadIndex().scopes.filterNot { it.storageKey == storageKey }.toMutableList()
        if (bytes != null) {
            existing += PersistentRemoteConfigScopeMetadata(storageKey, bytes)
        }

        val evictedStorageKeys = mutableSetOf<String>()
        while (existing.size > limits.maxScopes ||
            existing.sumOf { it.bytes.toLong() } > limits.maxTotalBytes.toLong()
        ) {
            val evicted = existing.removeFirst()
            evictedStorageKeys += evicted.storageKey
        }

        val index = existing.takeIf { it.isNotEmpty() }?.let {
            PersistentRemoteConfigIndex(INDEX_VERSION, it)
        }
        return PersistentRemoteConfigIndexUpdate(index, evictedStorageKeys)
    }

    private fun loadIndex(): PersistentRemoteConfigIndex {
        val raw = cache.getString(CACHE_INDEX_KEY, null) ?: return emptyIndex()
        val rawBytes = raw.toByteArray(Charsets.UTF_8).size
        val index = if (rawBytes <= MAX_REMOTE_CONFIG_INDEX_BYTES) {
            try {
                indexAdapter.fromJson(raw)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return index?.takeIf { it.isValid() } ?: emptyIndex()
    }

    private fun PersistentRemoteConfigIndex.isValid(): Boolean {
        val storageKeys = scopes.map { it.storageKey }
        return version == INDEX_VERSION &&
            scopes.size <= limits.maxScopes &&
            storageKeys.size == storageKeys.distinct().size &&
            scopes.all { metadata ->
                CACHE_STORAGE_KEY_PATTERN.matches(metadata.storageKey) &&
                    metadata.bytes > 0 &&
                    metadata.bytes <= limits.maxTotalBytes
            } &&
            scopes.sumOf { it.bytes.toLong() } <= limits.maxTotalBytes.toLong() &&
            scopes.all { it.matchesStoredEnvelope() }
    }

    private fun PersistentRemoteConfigScopeMetadata.matchesStoredEnvelope(): Boolean {
        val raw = cache.getString(storageKey, null)
        val actualBytes = raw?.utf8Size() ?: 0
        val envelope = raw
            ?.takeIf { actualBytes <= limits.maxTotalBytes }
            ?.let(::decodeEnvelope)
        return actualBytes == bytes && envelope.isValidForStorageKey(storageKey)
    }

    private fun emptyIndex() = PersistentRemoteConfigIndex(version = INDEX_VERSION, scopes = emptyList())

    private fun loadEnvelope(scope: RemoteConfigCacheScope): PersistentRemoteConfigEnvelope? {
        val storageKey = scope.storageKey
        memoryEnvelopes[storageKey]?.let {
            return it.takeIf { envelope -> envelope.isValidFor(scope, storageKey) }
        }
        val raw = cache.getString(storageKey, null)
        val envelope = raw
            ?.takeIf { it.utf8Size() <= limits.maxTotalBytes }
            ?.let(::decodeEnvelope)
        return when {
            raw == null -> null
            envelope.isValidFor(scope, storageKey) -> envelope.also { memoryEnvelopes[storageKey] = it!! }
            else -> {
                scheduleWrite(scope, emptyList())
                null
            }
        }
    }

    private fun decodeEnvelope(raw: String): PersistentRemoteConfigEnvelope? = try {
        adapter.fromJson(raw)
    } catch (_: Exception) {
        null
    }

    private fun PersistentRemoteConfigEnvelope?.isValidFor(
        scope: RemoteConfigCacheScope,
        storageKey: String,
    ): Boolean = isValidForStorageKey(storageKey) &&
        this?.projectKey == scope.projectKey &&
        environment == scope.environment &&
        userId == scope.userId

    private fun PersistentRemoteConfigEnvelope?.isValidForStorageKey(storageKey: String): Boolean =
        this != null &&
            version == CACHE_VERSION &&
            projectKey.isNotBlank() &&
            environment.isNotBlank() &&
            userId.isNotBlank() &&
            remoteConfigs.isNotEmpty() &&
            remoteConfigs.size <= limits.maxEntriesPerScope &&
            remoteConfigs.areValidForPersistence() &&
            RemoteConfigCacheScope(projectKey, environment, userId).storageKey == storageKey

    private fun List<QRemoteConfig>.areValidForPersistence(): Boolean {
        if (any { !it.isCorrect }) return false
        val contextKeys = map { it.source.contextKey.normalizedRemoteConfigContextKey() }
        return contextKeys.size == contextKeys.distinct().size
    }

    private fun List<QRemoteConfig>.areValidForRequestedPersistence(
        normalizedRequestedKeys: Set<String?>,
    ): Boolean = if (any { !it.isCorrect }) {
        false
    } else {
        val returnedKeys = map { config ->
            config.source.contextKey.normalizedRemoteConfigContextKey()
        }
        returnedKeys.size == returnedKeys.distinct().size &&
            returnedKeys.all { it in normalizedRequestedKeys }
    }

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    override fun currentScope(): RemoteConfigCacheScope? {
        val projectKey = config.primaryConfig.projectKey
        val environment = config.environment.name
        val userId = config.uid
        if (projectKey.isBlank() || userId.isBlank()) return null

        return RemoteConfigCacheScope(
            projectKey = projectKey,
            environment = environment,
            userId = userId,
        )
    }

    private val RemoteConfigCacheScope.storageKey: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$projectKey\u0000$environment\u0000$userId".toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte ->
                    val value = byte.toInt() and BYTE_MASK
                    "${HEX[value ushr NIBBLE_SHIFT]}${HEX[value and LOW_NIBBLE_MASK]}"
                }
            return "$CACHE_KEY_PREFIX$digest"
        }

    private companion object {
        const val CACHE_VERSION = 2
        const val INDEX_VERSION = 1
        const val CACHE_KEY_PREFIX = "qonversion_remote_config_lkg_"
        const val CACHE_INDEX_KEY = "qonversion_remote_config_lkg_index"
        const val HEX = "0123456789abcdef"
        const val BYTE_MASK = 0xff
        const val LOW_NIBBLE_MASK = 0x0f
        const val NIBBLE_SHIFT = 4
        val CACHE_STORAGE_KEY_PATTERN = Regex("^${Regex.escape(CACHE_KEY_PREFIX)}[0-9a-f]{64}$")

        val DEFAULT_PERSISTENCE_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "qonversion-remote-config-cache").apply { isDaemon = true }
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class PersistentRemoteConfigEnvelope(
    val version: Int,
    val projectKey: String,
    val environment: String,
    val userId: String,
    val remoteConfigs: List<QRemoteConfig>,
)

@JsonClass(generateAdapter = true)
internal data class PersistentRemoteConfigIndex(
    val version: Int,
    val scopes: List<PersistentRemoteConfigScopeMetadata>,
)

@JsonClass(generateAdapter = true)
internal data class PersistentRemoteConfigScopeMetadata(
    val storageKey: String,
    val bytes: Int,
)

private data class PersistentRemoteConfigIndexUpdate(
    val index: PersistentRemoteConfigIndex?,
    val evictedStorageKeys: Set<String>,
)

private data class PersistencePayload(
    val envelope: PersistentRemoteConfigEnvelope?,
    val json: String?,
)

private data class PendingCompletion(
    val isSuccessfulCommit: (PersistentRemoteConfigEnvelope?) -> Boolean,
    val callback: (Boolean) -> Unit,
)

private data class PendingState(
    val revision: Long?,
    val hasEnvelope: Boolean,
    val envelope: PersistentRemoteConfigEnvelope?,
    val completions: MutableList<PendingCompletion>?,
)

private data class PersistenceAttempt(
    val committed: Boolean,
    val indexUpdate: PersistentRemoteConfigIndexUpdate?,
) {
    companion object {
        fun failed() = PersistenceAttempt(false, null)
    }
}
