package com.qonversion.android.sdk

import com.qonversion.android.sdk.entity.Ads

internal data class QonversionConfig(
    val key: String,
    val sdkVersion: String,
    val ads: Ads
)