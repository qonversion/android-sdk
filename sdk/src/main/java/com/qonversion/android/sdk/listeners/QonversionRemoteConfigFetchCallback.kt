package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult

/**
 * Called exactly once, on the main thread, when a Remote Config fetch completes or times out.
 */
@ExperimentalQonversionApi
fun interface QonversionRemoteConfigFetchCallback {
    fun onResult(result: QRemoteConfigFetchResult)
}
