package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * The completion value of an activation.
 *
 * @param changed `true` when this activation made at least one key differ from the previously
 * activated release — i.e. "something changed since the last activation".
 * @param snapshot the snapshot that is current after the activation.
 * @param fetchStatus outcome of the fetch that preceded the activation, or `null` when the
 * activation was requested on its own.
 */
@ExperimentalQonversionApi
class QRemoteConfigActivationResult internal constructor(
    val changed: Boolean,
    val snapshot: QRemoteConfigSnapshot,
    val fetchStatus: QRemoteConfigFetchStatus? = null,
)
