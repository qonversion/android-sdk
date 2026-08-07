@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigResolvedValue
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshot
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotApplyPolicy
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotValueSource
import com.qonversion.android.sdk.internal.services.decodePortableRemoteConfigJson

/**
 * An immutable view of one Remote Config release.
 *
 * A snapshot never changes: holding it lets an app read several keys that are guaranteed to belong
 * to the same release, even if another release is activated meanwhile. Take a fresh snapshot from
 * `QRemoteConfigSnapshots.current` to observe a newer activation.
 *
 * Every read answers with a [QRemoteConfigValue] carrying the value **and** its
 * [QRemoteConfigSource], or `null` when no ladder position could produce a value: the key is
 * unknown to both the release and the bundled defaults, it was explicitly deleted from the release
 * and has no bundled default, or — for a typed read — every candidate was rejected by the decoder.
 */
@ExperimentalQonversionApi
class QRemoteConfigSnapshot internal constructor(
    private val snapshot: RemoteConfigSnapshot,
) {
    /** Identifier of the release this snapshot holds, or an empty string for a fallback-only one. */
    val releaseUid: String get() = snapshot.releaseUid

    /** Monotonic number of the release this snapshot holds, or `0` for a fallback-only one. */
    val releaseNumber: Long get() = snapshot.releaseNumber

    /** Every context key readable from this snapshot, including keys served by bundled defaults. */
    val contextKeys: Set<String> get() = snapshot.allKeys

    /**
     * Reads [contextKey] as the exact JSON text stored for it.
     *
     * Raw reads never reject a value, so their source is [QRemoteConfigSource.Server] or
     * [QRemoteConfigSource.Fallback] — [QRemoteConfigSource.Cache] is reachable only through a
     * typed read whose decoder rejected the current release's value.
     */
    fun rawValue(contextKey: String): QRemoteConfigValue<String>? =
        snapshot.rawValue(contextKey)?.toPublicValue { bytes -> bytes.toString(Charsets.UTF_8) }

    /**
     * Reads [contextKey] as an opaque JSON tree: a [Map], [List], [String], [Double], [Boolean],
     * or `null` for a JSON `null`.
     *
     * The wrapper stays non-null for a present JSON `null`, so an explicit null value remains
     * distinguishable from a missing key.
     */
    fun jsonValue(contextKey: String): QRemoteConfigValue<Any?>? =
        snapshot.value(contextKey) { bytes -> decodePortableRemoteConfigJson(bytes) }
            ?.toPublicValue { decoded -> decoded.value }

    /**
     * Reads [contextKey] through [decoder].
     *
     * A decoder that returns `null` (or throws) rejects the value and the read falls to the next
     * resolution-ladder position, which is what makes [QRemoteConfigSource.Cache] observable.
     */
    fun <T : Any> value(contextKey: String, decoder: QRemoteConfigDecoder<T>): QRemoteConfigValue<T>? =
        snapshot.value(contextKey) { bytes -> decoder.decode(bytes.toString(Charsets.UTF_8)) }
            ?.toPublicValue { decoded -> decoded }

    private fun <T, R> RemoteConfigResolvedValue<T>.toPublicValue(
        transform: (T) -> R,
    ): QRemoteConfigValue<R> = QRemoteConfigValue(
        value = transform(value),
        source = source.toPublicSource(),
        variationUid = variationUid,
        applyPolicy = applyPolicy.toPublicApplyPolicy(),
        metadataJson = metadataBytes.toMetadataJson(),
    )
}

/**
 * A release always carries a `metadata` member, and "no metadata" is spelled as the JSON literal
 * `null` on the wire. Collapsing it to a Kotlin `null` keeps `metadataJson != null` meaning
 * "there is metadata" for both server-served and bundled values.
 */
internal fun ByteArray?.toMetadataJson(): String? =
    this?.toString(Charsets.UTF_8)?.takeUnless { it == "null" }

internal fun RemoteConfigSnapshotValueSource.toPublicSource(): QRemoteConfigSource = when (this) {
    RemoteConfigSnapshotValueSource.Server -> QRemoteConfigSource.Server
    RemoteConfigSnapshotValueSource.Cache -> QRemoteConfigSource.Cache
    RemoteConfigSnapshotValueSource.Fallback -> QRemoteConfigSource.Fallback
}

internal fun RemoteConfigSnapshotApplyPolicy.toPublicApplyPolicy(): QRemoteConfigApplyPolicy = when (this) {
    RemoteConfigSnapshotApplyPolicy.OnNextActivate -> QRemoteConfigApplyPolicy.OnNextActivate
    RemoteConfigSnapshotApplyPolicy.Immediate -> QRemoteConfigApplyPolicy.Immediate
}
