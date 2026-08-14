package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotUpdate

/**
 * Describes one activation delivered to a config-update listener.
 *
 * The update is always a whole-release swap: [changedKeys] is the diff against the previously
 * activated release, and [snapshot] is the complete release that is now current.
 */
@ExperimentalQonversionApi
class QRemoteConfigUpdate internal constructor(
    private val update: RemoteConfigSnapshotUpdate,
) {
    /** The release that became current with this activation. */
    val snapshot: QRemoteConfigSnapshot = QRemoteConfigSnapshot(update.snapshot)

    /** Keys whose effective value differs from the previously activated release. */
    val changedKeys: Set<String> get() = update.changedKeys

    /** Raw JSON metadata attached to a changed key, or `null` when the key carries none. */
    fun metadataJson(contextKey: String): String? =
        update.metadataForKey(contextKey).toMetadataJson()

    /**
     * Apply policy declared for [contextKey] by the release that just became current, or `null`
     * when the key is not readable from it.
     *
     * A key with [QRemoteConfigApplyPolicy.Immediate] means this update was delivered without the
     * app calling `activate()` — the whole release was swapped atomically on admission.
     */
    fun applyPolicy(contextKey: String): QRemoteConfigApplyPolicy? =
        snapshot.rawValue(contextKey)?.applyPolicy
}
