package com.qonversion.android.sdk.dto.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi

/**
 * One resolved Remote Config read: the value plus where it came from.
 *
 * @param value the decoded value.
 * @param source the resolution-ladder position [value] was taken from.
 * @param variationUid identifier of the variation the value belongs to.
 * @param applyPolicy the apply policy declared for this key by the release it came from.
 * @param metadataJson raw JSON metadata attached to the key, or `null` when the release
 * declares none (the JSON literal `null` on the wire is reported as a Kotlin `null`).
 */
@ExperimentalQonversionApi
class QRemoteConfigValue<out T> internal constructor(
    val value: T,
    val source: QRemoteConfigSource,
    val variationUid: String,
    val applyPolicy: QRemoteConfigApplyPolicy,
    val metadataJson: String?,
)
