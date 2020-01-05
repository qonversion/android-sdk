package com.qonversion.android.sdk

import com.qonversion.android.sdk.entity.Ads

data class QonversionConfig(
    val key: String,
    val sdkVersion: String,
    val ads: Ads
)