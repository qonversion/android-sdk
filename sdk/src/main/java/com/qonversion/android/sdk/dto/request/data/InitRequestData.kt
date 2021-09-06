package com.qonversion.android.sdk.dto.request.data

import com.qonversion.android.sdk.QonversionLaunchCallback
import com.qonversion.android.sdk.entity.Purchase

data class InitRequestData(
    val installDate: Long,
    val idfa: String? = null,
    val purchases: List<Purchase>? = null,
    val callback: QonversionLaunchCallback? = null
)
