package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * Handle of a config-update subscription.
 *
 * Call [remove] to stop receiving updates. Removing twice is safe.
 */
@ExperimentalQonversionApi
fun interface QRemoteConfigSubscription {
    fun remove()
}
