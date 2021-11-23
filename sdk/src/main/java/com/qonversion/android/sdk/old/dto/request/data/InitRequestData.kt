package com.qonversion.android.sdk.old.dto.request.data

import com.qonversion.android.sdk.old.QonversionLaunchCallback
import com.qonversion.android.sdk.old.entity.Purchase

data class InitRequestData(
    val installDate: Long,
    val idfa: String? = null,
    val purchases: List<Purchase>? = null,
    val callback: QonversionLaunchCallback? = null
)
