package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * Where a resolved Remote Config value came from.
 *
 * The resolution ladder is always tried in this order: [Server], then [Cache], then [Fallback].
 * Every read result carries its position on that ladder, so a caller can tell a freshly targeted
 * value from a value that survived a failed decode or from the defaults bundled with the app.
 */
@ExperimentalQonversionApi
enum class QRemoteConfigSource {
    /** The value carried by the release this snapshot holds. */
    Server,

    /**
     * The previously activated release's value, reused because this snapshot's own value did not
     * survive the caller-supplied decode. Only reachable for typed reads.
     */
    Cache,

    /** The value from the Remote Config defaults bundled with the app. */
    Fallback,
}
