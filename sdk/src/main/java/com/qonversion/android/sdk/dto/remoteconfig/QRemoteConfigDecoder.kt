package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * Decodes one raw Remote Config JSON value into an app type.
 *
 * The decoder is the per-key validator seam of the snapshot: returning `null` (or throwing) means
 * "this raw value is not usable for this key", which makes the read fall to the next position of
 * the resolution ladder — the previously activated value, then the bundled default.
 *
 * Implementations must be deterministic and side-effect free: the same raw JSON is decoded again
 * on later reads, and a decoder that answers differently over time makes reads unstable.
 */
@ExperimentalQonversionApi
fun interface QRemoteConfigDecoder<out T> {
    /**
     * @param rawJson the exact JSON text stored for the key.
     * @return the decoded value, or `null` to reject this raw value.
     */
    fun decode(rawJson: String): T?
}
