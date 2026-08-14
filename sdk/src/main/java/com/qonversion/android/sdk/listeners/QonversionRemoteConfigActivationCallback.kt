package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigActivationResult

/**
 * Called exactly once, on the main thread, when a Remote Config activation completes.
 */
@ExperimentalQonversionApi
fun interface QonversionRemoteConfigActivationCallback {
    fun onResult(result: QRemoteConfigActivationResult)
}
