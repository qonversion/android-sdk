package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigUpdate

/**
 * Notified whenever a Remote Config release becomes current.
 *
 * Delivered on the main thread, after the swap is committed, so reading
 * `QRemoteConfigSnapshots.current` from the callback already observes the new release.
 */
@ExperimentalQonversionApi
fun interface QRemoteConfigUpdateListener {
    fun onRemoteConfigUpdated(update: QRemoteConfigUpdate)
}
