package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * Outcome of a Remote Config fetch attempt.
 *
 * None of these statuses changes what `QRemoteConfigSnapshots.current` returns: a fetched release becomes
 * current only through `activate()` — or immediately, when the release itself asks for it via
 * [QRemoteConfigApplyPolicy.Immediate].
 */
@ExperimentalQonversionApi
enum class QRemoteConfigFetchStatus {
    /** A new release was fetched and admitted. */
    Fetched,

    /** The server confirmed the held release is still current. */
    NotModified,

    /**
     * The caller's timeout elapsed first. The request keeps running in the background, and its
     * result is admitted when it arrives — it is simply no longer awaited.
     */
    TimedOut,

    /** The minimum fetch interval or a failure backoff blocked the attempt. */
    Throttled,

    /** The attempt failed. */
    Failed,

    /** An identity change replaced the scope this fetch belonged to. */
    Superseded,

    /** Remote Config v2 is not configured for this app, so no fetch was attempted. */
    NotConfigured,
}
