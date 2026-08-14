package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * How a fetched release asks to be applied.
 *
 * Activation is always a full atomic swap of the whole release — the policy decides *when* that
 * swap happens, never *which part* of the release is swapped.
 */
@ExperimentalQonversionApi
enum class QRemoteConfigApplyPolicy {
    /** The release becomes current only when the app calls `activate()`. */
    OnNextActivate,

    /**
     * The release is activated as soon as it is admitted. A single immediate key activates the
     * whole release, since a release is never applied partially.
     */
    Immediate,
}
