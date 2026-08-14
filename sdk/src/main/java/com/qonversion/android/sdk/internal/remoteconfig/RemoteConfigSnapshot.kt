package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.services.BUNDLED_REMOTE_CONFIG_DEFAULT_VALUE_MAX_BYTES
import com.qonversion.android.sdk.internal.services.BundledRemoteConfigDefaultsDocument
import com.qonversion.android.sdk.internal.services.isPortableRemoteConfigJson
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections

private const val REMOTE_CONFIG_METADATA_MAX_BYTES = 4 * 1024
private const val REMOTE_CONFIG_MAX_KEYS = 1_000
private const val REMOTE_CONFIG_MAX_RELEASE_BYTES = 4 * 1024 * 1024
private const val REMOTE_CONFIG_LOGICAL_KEY_MAX_BYTES = 256
private const val REMOTE_CONFIG_SCOPE_IDENTIFIER_MAX_BYTES = 256
private const val REMOTE_CONFIG_ENVIRONMENT_MAX_CODE_POINTS = 36
private const val REMOTE_CONFIG_UID_MAX_CODE_POINTS = 36
private const val PORTABLE_JSON_MAX_INTEGER = 9_007_199_254_740_991L
private val LOWERCASE_SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

internal enum class RemoteConfigSnapshotApplyPolicy {
    OnNextActivate,
    Immediate,
}

internal enum class RemoteConfigSnapshotValueSource {
    Server,
    Cache,
    Fallback,
}

internal data class RemoteConfigSnapshotScope(
    val projectKey: String,
    val environment: String,
    val canonicalUserId: String,
) {
    init {
        require(
            projectKey.isNotEmpty() && projectKey.hasValidSurrogatePairs() &&
                projectKey.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SCOPE_IDENTIFIER_MAX_BYTES,
        )
        require(
            environment.isNotEmpty() && environment.hasValidSurrogatePairs() &&
                environment.codePointCount(0, environment.length) <= REMOTE_CONFIG_ENVIRONMENT_MAX_CODE_POINTS,
        )
        require(
            canonicalUserId.isNotEmpty() && canonicalUserId.hasValidSurrogatePairs() &&
                canonicalUserId.toByteArray(Charsets.UTF_8).size <= REMOTE_CONFIG_SCOPE_IDENTIFIER_MAX_BYTES,
        )
    }
}

internal class RemoteConfigSnapshotEntry private constructor(
    val key: String,
    rawValue: ByteArray?,
    val variationUid: String?,
    val applyPolicy: RemoteConfigSnapshotApplyPolicy,
    metadata: ByteArray?,
) {
    private val storedRawValue = rawValue?.clone()
    private val storedMetadata = metadata?.clone()

    val isTombstone: Boolean get() = storedRawValue == null
    val rawValueBytes: ByteArray? get() = storedRawValue?.clone()
    val metadataBytes: ByteArray? get() = storedMetadata?.clone()
    internal val budgetBytes: Long
        get() = key.toByteArray().size.toLong() +
            (variationUid?.toByteArray()?.size ?: 0) +
            (storedRawValue?.size ?: 0) +
            (storedMetadata?.size ?: 0)

    init {
        require(
            key.isNotEmpty() && key.hasValidSurrogatePairs() &&
                key.toByteArray().size <= REMOTE_CONFIG_LOGICAL_KEY_MAX_BYTES,
        )
        if (storedRawValue == null) {
            require(variationUid == null)
            require(storedMetadata == null)
        } else {
            require(
                !variationUid.isNullOrEmpty() && variationUid.hasValidUidLength() &&
                    variationUid.hasValidSurrogatePairs(),
            )
            require(storedRawValue.size <= BUNDLED_REMOTE_CONFIG_DEFAULT_VALUE_MAX_BYTES)
            require(isPortableRemoteConfigJson(storedRawValue))
            require(storedMetadata == null || (
                storedMetadata.size <= REMOTE_CONFIG_METADATA_MAX_BYTES &&
                    isPortableRemoteConfigJson(storedMetadata)
                ))
        }
    }

    internal fun contentEquals(other: RemoteConfigSnapshotEntry?): Boolean =
        other != null &&
            key == other.key &&
            storedRawValue.contentEqualsNullable(other.storedRawValue) &&
            variationUid == other.variationUid &&
            applyPolicy == other.applyPolicy &&
            storedMetadata.contentEqualsNullable(other.storedMetadata)

    companion object {
        fun value(
            key: String,
            rawValue: ByteArray,
            variationUid: String,
            applyPolicy: RemoteConfigSnapshotApplyPolicy,
            metadata: ByteArray?,
        ) = RemoteConfigSnapshotEntry(key, rawValue, variationUid, applyPolicy, metadata)

        fun tombstone(key: String) = RemoteConfigSnapshotEntry(
            key = key,
            rawValue = null,
            variationUid = null,
            applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
            metadata = null,
        )
    }
}

/**
 * One admitted Remote Config release.
 *
 * [contextFingerprint] is **informational**: it records which targeting context the gateway resolved
 * this response for, which is useful in a bug report or a log line. It is not an admission input.
 * The fingerprint hashes mutable targeting context (app/OS version, locale, purchases, properties);
 * it rotates legitimately and MUST NOT be pinned across fetches. Identity isolation is the session's
 * job.
 */
internal class RemoteConfigSnapshotRelease(
    val releaseUid: String,
    val releaseNumber: Long,
    val manifestContentHash: String,
    entries: Collection<RemoteConfigSnapshotEntry>,
    canonicalBody: ByteArray? = null,
    val strongETag: String? = null,
    val contextFingerprint: String? = null,
    val admissionToken: Long = 0,
) {
    private val entriesByKey: Map<String, RemoteConfigSnapshotEntry>
    private val storedCanonicalBody = canonicalBody?.clone()

    val entries: Map<String, RemoteConfigSnapshotEntry> get() = entriesByKey
    val canonicalBodyBytes: ByteArray? get() = storedCanonicalBody?.clone()
    internal val hasCanonicalBody: Boolean get() = storedCanonicalBody != null
    val bodyDigest: String? get() = strongETag?.removeSurrounding("\"")
    internal val contentDigest: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        calculateContentDigest()
    }
    val containsImmediateEntry: Boolean
        get() = entriesByKey.values.any { it.applyPolicy == RemoteConfigSnapshotApplyPolicy.Immediate }

    init {
        require(releaseUid.isNotEmpty() && releaseUid.hasValidUidLength() && releaseUid.hasValidSurrogatePairs())
        require(releaseNumber in 1..PORTABLE_JSON_MAX_INTEGER)
        require(LOWERCASE_SHA256_PATTERN.matches(manifestContentHash))
        require(contextFingerprint == null || LOWERCASE_SHA256_PATTERN.matches(contextFingerprint))
        require(admissionToken >= 0)
        require(entries.count { !it.isTombstone } <= REMOTE_CONFIG_MAX_KEYS)
        require(entries.count(RemoteConfigSnapshotEntry::isTombstone) <= REMOTE_CONFIG_MAX_KEYS)
        require(entries.map { it.key }.distinct().size == entries.size)
        require((storedCanonicalBody == null) == (strongETag == null))
        if (storedCanonicalBody != null) {
            require(storedCanonicalBody.size <= REMOTE_CONFIG_SNAPSHOT_ENVELOPE_MAX_BYTES)
            require(remoteConfigStrongETagDigest(storedCanonicalBody, requireNotNull(strongETag)) != null)
        }
        val aggregateBytes = releaseUid.toByteArray().size.toLong() + manifestContentHash.length +
            entries.sumOf(RemoteConfigSnapshotEntry::budgetBytes)
        require(aggregateBytes <= REMOTE_CONFIG_MAX_RELEASE_BYTES)
        entriesByKey = Collections.unmodifiableMap(entries.associateBy { it.key })
    }

    fun entry(key: String): RemoteConfigSnapshotEntry? = entriesByKey[key]

    internal fun contentEquals(other: RemoteConfigSnapshotRelease?): Boolean =
        other != null && releaseUid == other.releaseUid && releaseNumber == other.releaseNumber &&
            manifestContentHash == other.manifestContentHash && contextFingerprint == other.contextFingerprint &&
            entriesByKey.size == other.entriesByKey.size &&
            entriesByKey.all { (key, entry) -> entry.contentEquals(other.entriesByKey[key]) }

    internal fun withAdmissionToken(token: Long): RemoteConfigSnapshotRelease = RemoteConfigSnapshotRelease(
        releaseUid = releaseUid,
        releaseNumber = releaseNumber,
        manifestContentHash = manifestContentHash,
        entries = entriesByKey.values,
        canonicalBody = canonicalBodyBytes,
        strongETag = strongETag,
        contextFingerprint = contextFingerprint,
        admissionToken = token,
    )

    private fun calculateContentDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateLengthPrefixed("remote-config-snapshot-release-v1".encodeToByteArray())
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(admissionToken).array())
        digest.updateLengthPrefixed(releaseUid.encodeToByteArray())
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(releaseNumber).array())
        digest.updateLengthPrefixed(manifestContentHash.encodeToByteArray())
        digest.updateNullable(contextFingerprint?.encodeToByteArray())
        entriesByKey.toSortedMap().values.forEach { entry ->
            digest.updateLengthPrefixed(entry.key.encodeToByteArray())
            digest.update(if (entry.isTombstone) TOMBSTONE_MARKER else VALUE_MARKER)
            digest.updateNullable(entry.rawValueBytes)
            digest.updateNullable(entry.variationUid?.encodeToByteArray())
            digest.update(
                when (entry.applyPolicy) {
                    RemoteConfigSnapshotApplyPolicy.OnNextActivate -> ON_NEXT_ACTIVATE_MARKER
                    RemoteConfigSnapshotApplyPolicy.Immediate -> IMMEDIATE_MARKER
                },
            )
            digest.updateNullable(entry.metadataBytes)
        }
        return digest.digest().toLowercaseHex()
    }
}

internal class RemoteConfigScopedBundledRelease(
    val projectKey: String,
    val environment: String,
    val release: RemoteConfigSnapshotRelease,
) {
    init {
        RemoteConfigSnapshotScope(projectKey, environment, "bundle-scope-validation")
    }

    fun releaseFor(scope: RemoteConfigSnapshotScope?): RemoteConfigSnapshotRelease? = when {
        scope == null -> release
        scope.projectKey == projectKey && scope.environment == environment -> release
        else -> null
    }
}

internal data class RemoteConfigSnapshotState(
    val candidate: RemoteConfigSnapshotRelease? = null,
    val active: RemoteConfigSnapshotRelease? = null,
    val previous: RemoteConfigSnapshotRelease? = null,
    val didActivate: Boolean = false,
    val latestAdmissionToken: Long = maxOf(
        candidate?.admissionToken ?: 0,
        active?.admissionToken ?: 0,
        previous?.admissionToken ?: 0,
    ),
) {
    init {
        val highestSlotToken = maxOf(
            candidate?.admissionToken ?: 0,
            active?.admissionToken ?: 0,
            previous?.admissionToken ?: 0,
        )
        require(active != null || previous == null)
        require(didActivate || (active == null && previous == null))
        require(latestAdmissionToken >= highestSlotToken)
        require(candidate == null || active == null || candidate.admissionToken >= active.admissionToken)
        require(
            candidate == null || active == null || candidate.admissionToken != active.admissionToken ||
                candidate.contentEquals(active),
        )
        require(previous == null || active == null || previous.admissionToken < active.admissionToken)
    }
}

private fun MessageDigest.updateLengthPrefixed(bytes: ByteArray) {
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    update(bytes)
}

private fun MessageDigest.updateNullable(bytes: ByteArray?) {
    if (bytes == null) {
        update(NULL_MARKER)
    } else {
        update(PRESENT_MARKER)
        updateLengthPrefixed(bytes)
    }
}

private fun ByteArray.toLowercaseHex(): String = joinToString(separator = "") { byte ->
    val value = byte.toInt() and BYTE_MASK
    "${HEX[value ushr NIBBLE_SHIFT]}${HEX[value and LOW_NIBBLE_MASK]}"
}

private const val NULL_MARKER: Byte = 0
private const val PRESENT_MARKER: Byte = 1
private const val TOMBSTONE_MARKER: Byte = 2
private const val VALUE_MARKER: Byte = 3
private const val ON_NEXT_ACTIVATE_MARKER: Byte = 4
private const val IMMEDIATE_MARKER: Byte = 5
private const val BYTE_MASK = 0xff
private const val LOW_NIBBLE_MASK = 0x0f
private const val NIBBLE_SHIFT = 4
private const val HEX = "0123456789abcdef"

internal class RemoteConfigResolvedValue<T>(
    val value: T,
    val source: RemoteConfigSnapshotValueSource,
    val variationUid: String,
    val applyPolicy: RemoteConfigSnapshotApplyPolicy,
    metadata: ByteArray?,
) {
    private val storedMetadata = metadata?.clone()
    val metadataBytes: ByteArray? get() = storedMetadata?.clone()
}

/**
 * Told that a typed read of [logicalKey] could not decode the value release [releaseNumber] served.
 *
 * The observer is a pure side channel: it is invoked AFTER the resolution ladder has already fallen
 * through to the next rung, it may not throw into the read, and it must do nothing but enqueue —
 * see [RemoteConfigTelemetrySender.recordDecodeFailure].
 */
internal fun interface RemoteConfigDecodeFailureObserver {
    fun onDecodeFailure(logicalKey: String, releaseNumber: Long)
}

internal class RemoteConfigSnapshot(
    private val primaryRelease: RemoteConfigSnapshotRelease?,
    private val previousRelease: RemoteConfigSnapshotRelease?,
    private val bundledRelease: RemoteConfigSnapshotRelease?,
    private val decodeFailureObserver: RemoteConfigDecodeFailureObserver = NO_DECODE_FAILURE_OBSERVER,
) {
    val releaseUid: String get() = primaryRelease?.releaseUid.orEmpty()
    val releaseNumber: Long get() = primaryRelease?.releaseNumber ?: 0
    val manifestContentHash: String get() = primaryRelease?.manifestContentHash.orEmpty()
    val allKeys: Set<String> = Collections.unmodifiableSet(
        buildSet {
            primaryRelease?.entries?.keys?.let(::addAll)
            bundledRelease?.entries?.keys?.let(::addAll)
        },
    )
    val readableKeys: Set<String> = Collections.unmodifiableSet(
        buildSet {
            primaryRelease?.entries?.values
                ?.filterNot(RemoteConfigSnapshotEntry::isTombstone)
                ?.mapTo(this, RemoteConfigSnapshotEntry::key)
            bundledRelease?.entries?.values
                ?.filterNot(RemoteConfigSnapshotEntry::isTombstone)
                ?.mapTo(this, RemoteConfigSnapshotEntry::key)
        },
    )

    @Suppress("ReturnCount")
    fun rawValue(key: String): RemoteConfigResolvedValue<ByteArray>? {
        val candidate = primaryRelease?.entry(key)?.takeUnless { it.isTombstone }
            ?.let { it to RemoteConfigSnapshotValueSource.Server }
            ?: bundledRelease?.entry(key)?.takeUnless { it.isTombstone }
                ?.let { it to RemoteConfigSnapshotValueSource.Fallback }
            ?: return null
        val raw = candidate.first.rawValueBytes ?: return null
        return candidate.first.resolve(raw, candidate.second)
    }

    @Suppress("ReturnCount")
    fun <T> value(key: String, decoder: (ByteArray) -> T?): RemoteConfigResolvedValue<T>? {
        val primary = primaryRelease?.entry(key)
        if (primary != null && !primary.isTombstone) {
            primary.decode(decoder, RemoteConfigSnapshotValueSource.Server)?.let { return it }
            // The served release carries the key but the app's decoder refused its value — the one
            // failure mode the ladder hides completely, which is why it is reported here and only
            // here. Reported strictly after the decode and strictly before the ladder continues:
            // the value the caller receives is byte-for-byte what it would be without telemetry.
            reportDecodeFailure(key)
            previousRelease?.entry(key)
                ?.takeUnless { it.isTombstone }
                ?.decode(decoder, RemoteConfigSnapshotValueSource.Cache)
                ?.let { return it }
        }
        return bundledRelease?.entry(key)
            ?.takeUnless { it.isTombstone }
            ?.decode(decoder, RemoteConfigSnapshotValueSource.Fallback)
    }

    fun metadataForKey(key: String): ByteArray? {
        val entry = primaryRelease?.entry(key)?.takeUnless { it.isTombstone }
            ?: bundledRelease?.entry(key)?.takeUnless { it.isTombstone }
        return entry?.metadataBytes
    }

    internal fun effectiveEntry(key: String): RemoteConfigSnapshotEntry? =
        primaryRelease?.entry(key)?.takeUnless { it.isTombstone }
            ?: bundledRelease?.entry(key)?.takeUnless { it.isTombstone }

    private fun reportDecodeFailure(key: String) {
        try {
            decodeFailureObserver.onDecodeFailure(key, primaryRelease?.releaseNumber ?: 0)
        } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
            // Telemetry can never affect a read.
        }
    }

    private fun <T> RemoteConfigSnapshotEntry.decode(
        decoder: (ByteArray) -> T?,
        source: RemoteConfigSnapshotValueSource,
    ): RemoteConfigResolvedValue<T>? {
        val decoded = try {
            rawValueBytes?.let(decoder)
        } catch (_: Exception) {
            null
        } ?: return null
        return resolve(decoded, source)
    }

    private fun <T> RemoteConfigSnapshotEntry.resolve(
        value: T,
        source: RemoteConfigSnapshotValueSource,
    ) = RemoteConfigResolvedValue(
        value = value,
        source = source,
        variationUid = requireNotNull(variationUid),
        applyPolicy = applyPolicy,
        metadata = metadataBytes,
    )

    private companion object {
        val NO_DECODE_FAILURE_OBSERVER = RemoteConfigDecodeFailureObserver { _, _ -> }
    }
}

internal class RemoteConfigSnapshotUpdate(
    val snapshot: RemoteConfigSnapshot,
    changedKeys: Set<String>,
    metadataByKey: Map<String, ByteArray>,
) {
    val changedKeys: Set<String> = Collections.unmodifiableSet(changedKeys.toSet())
    private val storedMetadata = metadataByKey.mapValues { (_, value) -> value.clone() }

    fun metadataForKey(key: String): ByteArray? = storedMetadata[key]?.clone()
}

internal fun BundledRemoteConfigDefaultsDocument.toScopedRemoteConfigSnapshotRelease(projectKey: String) =
    RemoteConfigScopedBundledRelease(
        projectKey = projectKey,
        environment = environmentUid,
        release = RemoteConfigSnapshotRelease(
            releaseUid = releaseUid,
            releaseNumber = releaseNumber,
            manifestContentHash = manifestContentHash,
            entries = allDefaults().map { value ->
                RemoteConfigSnapshotEntry.value(
                    key = value.key,
                    rawValue = value.rawJsonBytes,
                    variationUid = value.variationUid,
                    applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                    metadata = null,
                )
            },
        ),
    )

private fun String.hasValidUidLength(): Boolean =
    codePointCount(0, length) <= REMOTE_CONFIG_UID_MAX_CODE_POINTS

@Suppress("ReturnCount")
private fun String.hasValidSurrogatePairs(): Boolean {
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                index += 2
            }
            character.isLowSurrogate() -> return false
            else -> index += 1
        }
    }
    return true
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}
