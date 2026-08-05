package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotApplyPolicy
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotEntry
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotRelease
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotScope
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotState
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.security.MessageDigest

internal const val REMOTE_CONFIG_SNAPSHOT_INDEX_KEY = "qonversion_remote_config_v2_snapshot_index"

private const val REMOTE_CONFIG_SNAPSHOT_STORAGE_PREFIX = "qonversion_remote_config_v2_snapshot_"
private const val REMOTE_CONFIG_SNAPSHOT_ENVELOPE_VERSION = 1
private const val REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION = 1
private const val DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_SCOPES = 16
private const val DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_STATE_BYTES = 20 * 1024 * 1024
private const val DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_TOTAL_BYTES = 32 * 1024 * 1024
private const val REMOTE_CONFIG_SNAPSHOT_MAX_INDEX_BYTES = 64 * 1024
private const val BYTE_MASK = 0xff
private const val LOW_NIBBLE_MASK = 0x0f
private const val NIBBLE_SHIFT = 4
private const val HEX = "0123456789abcdef"
private val REMOTE_CONFIG_SNAPSHOT_STORAGE_KEY_PATTERN =
    Regex("^${Regex.escape(REMOTE_CONFIG_SNAPSHOT_STORAGE_PREFIX)}[0-9a-f]{64}$")

internal enum class RemoteConfigSnapshotLoadStatus {
    Found,
    Missing,
    Failed,
}

internal data class RemoteConfigSnapshotLoadResult(
    val status: RemoteConfigSnapshotLoadStatus,
    val state: RemoteConfigSnapshotState? = null,
) {
    init {
        require((status == RemoteConfigSnapshotLoadStatus.Found) == (state != null))
    }
}

internal interface RemoteConfigSnapshotStore {
    fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult
    fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean
}

internal class PersistentRemoteConfigSnapshotStore(
    private val cache: Cache,
    moshi: Moshi,
    private val maxScopes: Int = DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_SCOPES,
    private val maxStateBytes: Int = DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_STATE_BYTES,
    private val maxTotalBytes: Int = DEFAULT_REMOTE_CONFIG_SNAPSHOT_MAX_TOTAL_BYTES,
) : RemoteConfigSnapshotStore {
    private val envelopeAdapter = moshi.adapter(PersistedRemoteConfigSnapshotEnvelope::class.java).failOnUnknown()
    private val indexAdapter = moshi.adapter(PersistedRemoteConfigSnapshotIndex::class.java).failOnUnknown()

    init {
        require(maxScopes > 0)
        require(maxStateBytes > 0)
        require(maxTotalBytes > 0)
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun load(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotLoadResult = try {
        loadTrusted(scope)?.let { state ->
            RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Found, state)
        } ?: RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Missing)
    } catch (_: Exception) {
        RemoteConfigSnapshotLoadResult(RemoteConfigSnapshotLoadStatus.Failed)
    }

    @Suppress("ReturnCount")
    private fun loadTrusted(scope: RemoteConfigSnapshotScope): RemoteConfigSnapshotState? {
        val storageKey = remoteConfigSnapshotStorageKey(scope)
        val index = loadIndex(clearInvalid = true)
        val rawEnvelope = cache.getString(storageKey, null)
        val envelopeBytes = rawEnvelope?.toByteArray(Charsets.UTF_8)?.size
        val envelope = rawEnvelope
            ?.takeIf {
                requireNotNull(envelopeBytes) <= maxStateBytes && envelopeBytes <= maxTotalBytes
            }
            ?.let(::decodeEnvelope)
        val decoded = envelope
            ?.takeIf { it.matches(scope, storageKey) }
            ?.state
            ?.toDecodedModel()
        if (decoded != null) {
            val rewritten = decoded.requiresRewrite && save(scope, decoded.state)
            if (!rewritten) {
                if (storageKey in index.storageKeys) {
                    promoteAfterRead(storageKey, index)
                } else {
                    admitRecoveredAfterRead(storageKey, requireNotNull(envelopeBytes), index)
                }
            }
            return decoded.state
        }
        removeInvalidEnvelope(storageKey, index)
        return null
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun save(scope: RemoteConfigSnapshotScope, state: RemoteConfigSnapshotState): Boolean {
        val storageKey = remoteConfigSnapshotStorageKey(scope)
        val envelope = PersistedRemoteConfigSnapshotEnvelope(
            version = REMOTE_CONFIG_SNAPSHOT_ENVELOPE_VERSION,
            projectKey = scope.projectKey,
            environment = scope.environment,
            canonicalUserId = scope.canonicalUserId,
            state = state.toPersisted(),
        )
        val stateJson = try {
            envelopeAdapter.toJson(envelope)
        } catch (_: Exception) {
            return false
        }
        val stateBytes = stateJson.toByteArray(Charsets.UTF_8).size
        if (stateBytes > maxStateBytes || stateBytes > maxTotalBytes) return false

        val existing = try {
            loadIndex(clearInvalid = false).storageKeys
        } catch (_: Exception) {
            return false
        }
        val admitted = try {
            existing.filter { it != storageKey }.mapNotNull { key ->
                admittedEnvelopeSize(key)?.let { bytes -> StoredEnvelope(key, bytes) }
            }
        } catch (_: Exception) {
            return false
        }
        val retained = boundedNewest(admitted + StoredEnvelope(storageKey, stateBytes))
        val retainedKeys = retained.map(StoredEnvelope::storageKey)
        val evicted = (existing.toSet() - retainedKeys.toSet()) - storageKey
        val indexJson = try {
            indexAdapter.toJson(
                PersistedRemoteConfigSnapshotIndex(
                    version = REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION,
                    storageKeys = retainedKeys,
                ),
            )
        } catch (_: Exception) {
            return false
        }
        if (indexJson.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_SNAPSHOT_MAX_INDEX_BYTES) return false

        return try {
            cache.updateStringsDurably(
                values = mapOf(
                    storageKey to stateJson,
                    REMOTE_CONFIG_SNAPSHOT_INDEX_KEY to indexJson,
                ),
                removedKeys = evicted,
            )
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("ReturnCount")
    private fun loadIndex(clearInvalid: Boolean): PersistedRemoteConfigSnapshotIndex {
        val raw = cache.getString(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY, null) ?: return emptyIndex()
        val decoded = try {
            raw.takeIf { it.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SNAPSHOT_MAX_INDEX_BYTES }
                ?.let(indexAdapter::fromJson)
                ?.takeIf { it.isValid() }
        } catch (_: Exception) {
            null
        }
        if (decoded != null) return decoded
        if (clearInvalid) clearInvalidIndex()
        return emptyIndex()
    }

    private fun PersistedRemoteConfigSnapshotIndex.isValid(): Boolean =
        version == REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION &&
            storageKeys.size <= maxScopes &&
            storageKeys.size == storageKeys.distinct().size &&
            storageKeys.all(REMOTE_CONFIG_SNAPSHOT_STORAGE_KEY_PATTERN::matches)

    private fun PersistedRemoteConfigSnapshotEnvelope.matches(
        scope: RemoteConfigSnapshotScope,
        storageKey: String,
    ): Boolean = version == REMOTE_CONFIG_SNAPSHOT_ENVELOPE_VERSION &&
        projectKey == scope.projectKey && environment == scope.environment &&
        canonicalUserId == scope.canonicalUserId &&
        remoteConfigSnapshotStorageKey(scope) == storageKey

    private fun decodeEnvelope(raw: String): PersistedRemoteConfigSnapshotEnvelope? = try {
        envelopeAdapter.fromJson(raw)
    } catch (_: Exception) {
        null
    }

    @Suppress("ReturnCount")
    private fun admittedEnvelopeSize(storageKey: String): Int? {
        val raw = cache.getString(storageKey, null) ?: return null
        val rawBytes = raw.toByteArray(Charsets.UTF_8).size
        if (rawBytes > maxStateBytes || rawBytes > maxTotalBytes) return null
        val envelope = decodeEnvelope(raw) ?: return null
        val scope = try {
            RemoteConfigSnapshotScope(
                projectKey = envelope.projectKey,
                environment = envelope.environment,
                canonicalUserId = envelope.canonicalUserId,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!envelope.matches(scope, storageKey)) return null
        val decoded = envelope.state.toDecodedModel()
        if (decoded.requiresRewrite && decoded.state == RemoteConfigSnapshotState()) return null
        return rawBytes
    }

    private fun boundedNewest(envelopes: List<StoredEnvelope>): List<StoredEnvelope> {
        val retained = envelopes.takeLast(maxScopes).toMutableList()
        var totalBytes = retained.sumOf { envelope -> envelope.bytes.toLong() }
        while (totalBytes > maxTotalBytes && retained.size > 1) {
            totalBytes -= retained.removeAt(0).bytes
        }
        return retained
    }

    private fun removeInvalidEnvelope(
        storageKey: String,
        index: PersistedRemoteConfigSnapshotIndex,
    ) {
        val remaining = index.storageKeys - storageKey
        val values = if (remaining.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                REMOTE_CONFIG_SNAPSHOT_INDEX_KEY to indexAdapter.toJson(
                    PersistedRemoteConfigSnapshotIndex(REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION, remaining),
                ),
            )
        }
        val removed = buildSet {
            add(storageKey)
            if (remaining.isEmpty()) add(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY)
        }
        try {
            cache.updateStringsDurably(values, removed)
        } catch (_: Exception) {
            // Reads stay fail-closed if corruption cleanup cannot be committed.
        }
    }

    @Suppress("ReturnCount")
    private fun admitRecoveredAfterRead(
        storageKey: String,
        storageBytes: Int,
        index: PersistedRemoteConfigSnapshotIndex,
    ) {
        val admitted = try {
            index.storageKeys.mapNotNull { key ->
                admittedEnvelopeSize(key)?.let { bytes -> StoredEnvelope(key, bytes) }
            }
        } catch (_: Exception) {
            return
        }
        val retained = boundedNewest(admitted + StoredEnvelope(storageKey, storageBytes))
        val retainedKeys = retained.map(StoredEnvelope::storageKey)
        val removed = index.storageKeys.toSet() - retainedKeys.toSet()
        val indexJson = try {
            indexAdapter.toJson(
                PersistedRemoteConfigSnapshotIndex(
                    version = REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION,
                    storageKeys = retainedKeys,
                ),
            )
        } catch (_: Exception) {
            return
        }
        if (indexJson.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_SNAPSHOT_MAX_INDEX_BYTES) return
        try {
            cache.updateStringsDurably(
                values = mapOf(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY to indexJson),
                removedKeys = removed,
            )
        } catch (_: Exception) {
            // An exact valid envelope remains safe to serve even if its index cannot be repaired.
        }
    }

    @Suppress("ReturnCount")
    private fun promoteAfterRead(
        storageKey: String,
        index: PersistedRemoteConfigSnapshotIndex,
    ) {
        if (index.storageKeys.lastOrNull() == storageKey) return
        val promoted = PersistedRemoteConfigSnapshotIndex(
            version = REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION,
            storageKeys = (index.storageKeys - storageKey) + storageKey,
        )
        val indexJson = try {
            indexAdapter.toJson(promoted)
        } catch (_: Exception) {
            return
        }
        if (indexJson.toByteArray(Charsets.UTF_8).size > REMOTE_CONFIG_SNAPSHOT_MAX_INDEX_BYTES) return
        try {
            cache.updateStringsDurably(
                values = mapOf(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY to indexJson),
                removedKeys = emptySet(),
            )
        } catch (_: Exception) {
            // A failed recency hint must never make an otherwise valid snapshot unavailable.
        }
    }

    private fun clearInvalidIndex() {
        try {
            cache.updateStringsDurably(emptyMap(), setOf(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY))
        } catch (_: Exception) {
            // The invalid index remains unusable and no scope is trusted from it.
        }
    }

    private fun emptyIndex() = PersistedRemoteConfigSnapshotIndex(
        version = REMOTE_CONFIG_SNAPSHOT_INDEX_VERSION,
        storageKeys = emptyList(),
    )

    private data class StoredEnvelope(
        val storageKey: String,
        val bytes: Int,
    )
}

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigSnapshotIndex(
    val version: Int,
    val storageKeys: List<String>,
)

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigSnapshotEnvelope(
    val version: Int,
    val projectKey: String,
    val environment: String,
    val canonicalUserId: String,
    val state: PersistedRemoteConfigSnapshotState,
)

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigSnapshotState(
    val candidate: PersistedRemoteConfigSnapshotRelease?,
    val active: PersistedRemoteConfigSnapshotRelease?,
    val previous: PersistedRemoteConfigSnapshotRelease?,
    val didActivate: Boolean,
)

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigSnapshotRelease(
    val releaseUid: String,
    val releaseNumber: Long,
    val manifestContentHash: String,
    val entries: List<PersistedRemoteConfigSnapshotEntry>,
)

@JsonClass(generateAdapter = true)
internal data class PersistedRemoteConfigSnapshotEntry(
    val key: String,
    val rawBase64: String?,
    val variationUid: String?,
    val applyPolicy: Int,
    val metadataBase64: String?,
)

private data class DecodedRemoteConfigSnapshotState(
    val state: RemoteConfigSnapshotState,
    val requiresRewrite: Boolean,
)

@Suppress("ComplexMethod")
private fun PersistedRemoteConfigSnapshotState.toDecodedModel(): DecodedRemoteConfigSnapshotState {
    var candidateModel = candidate?.toModel()
    var activeModel = active?.toModel()
    var previousModel = previous?.toModel()
    var requiresRewrite =
        (candidate != null && candidateModel == null) ||
            (active != null && activeModel == null) ||
            (previous != null && previousModel == null)

    if (!didActivate && activeModel != null) {
        activeModel = null
        previousModel = null
        requiresRewrite = true
    }
    if (activeModel == null && previousModel != null) {
        previousModel = null
        requiresRewrite = true
    }
    if (candidateModel != null && activeModel != null &&
        candidateModel.releaseNumber < activeModel.releaseNumber
    ) {
        candidateModel = null
        requiresRewrite = true
    }
    if (candidateModel != null && activeModel != null &&
        candidateModel.releaseNumber == activeModel.releaseNumber
    ) {
        if (candidateModel.contentEquals(activeModel)) {
            candidateModel = activeModel
        } else {
            candidateModel = null
            requiresRewrite = true
        }
    }
    if (previousModel != null && activeModel != null &&
        previousModel.releaseNumber >= activeModel.releaseNumber
    ) {
        previousModel = null
        requiresRewrite = true
    }
    return DecodedRemoteConfigSnapshotState(
        state = RemoteConfigSnapshotState(
            candidate = candidateModel,
            active = activeModel,
            previous = previousModel,
            didActivate = didActivate,
        ),
        requiresRewrite = requiresRewrite,
    )
}

@Suppress("ReturnCount")
private fun PersistedRemoteConfigSnapshotRelease.toModel(): RemoteConfigSnapshotRelease? {
    return try {
        RemoteConfigSnapshotRelease(
            releaseUid = releaseUid,
            releaseNumber = releaseNumber,
            manifestContentHash = manifestContentHash,
            entries = entries.map { it.toModel() ?: return null },
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Suppress("ReturnCount")
private fun PersistedRemoteConfigSnapshotEntry.toModel(): RemoteConfigSnapshotEntry? {
    return try {
        val policy = when (applyPolicy) {
            1 -> RemoteConfigSnapshotApplyPolicy.OnNextActivate
            2 -> RemoteConfigSnapshotApplyPolicy.Immediate
            else -> return null
        }
        val raw = rawBase64.decodeCanonicalBase64()
        val metadata = metadataBase64.decodeCanonicalBase64()
        when {
            rawBase64 == null && metadataBase64 == null && variationUid == null ->
                RemoteConfigSnapshotEntry.tombstone(key)
            raw == null || variationUid == null || (metadataBase64 != null && metadata == null) -> null
            else -> RemoteConfigSnapshotEntry.value(key, raw, variationUid, policy, metadata)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Suppress("ReturnCount")
private fun String?.decodeCanonicalBase64(): ByteArray? {
    if (this == null) return null
    val decoded = decodeBase64() ?: return null
    return decoded.takeIf { it.base64() == this }?.toByteArray()
}

private fun RemoteConfigSnapshotState.toPersisted() = PersistedRemoteConfigSnapshotState(
    candidate = candidate?.toPersisted(),
    active = active?.toPersisted(),
    previous = previous?.toPersisted(),
    didActivate = didActivate,
)

private fun RemoteConfigSnapshotRelease.toPersisted() = PersistedRemoteConfigSnapshotRelease(
    releaseUid = releaseUid,
    releaseNumber = releaseNumber,
    manifestContentHash = manifestContentHash,
    entries = entries.values.sortedBy { it.key }.map { entry ->
        PersistedRemoteConfigSnapshotEntry(
            key = entry.key,
            rawBase64 = entry.rawValueBytes?.toByteString()?.base64(),
            variationUid = entry.variationUid,
            applyPolicy = when (entry.applyPolicy) {
                RemoteConfigSnapshotApplyPolicy.OnNextActivate -> 1
                RemoteConfigSnapshotApplyPolicy.Immediate -> 2
            },
            metadataBase64 = entry.metadataBytes?.toByteString()?.base64(),
        )
    },
)

internal fun remoteConfigSnapshotStorageKey(scope: RemoteConfigSnapshotScope): String {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    listOf(scope.projectKey, scope.environment, scope.canonicalUserId).forEach { component ->
        val bytes = component.toByteArray(Charsets.UTF_8)
        messageDigest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        messageDigest.update(bytes)
    }
    val digest = messageDigest.digest()
        .joinToString(separator = "") { byte ->
            val value = byte.toInt() and BYTE_MASK
            "${HEX[value ushr NIBBLE_SHIFT]}${HEX[value and LOW_NIBBLE_MASK]}"
        }
    return "$REMOTE_CONFIG_SNAPSHOT_STORAGE_PREFIX$digest"
}
