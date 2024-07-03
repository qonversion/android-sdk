package com.qonversion.android.sdk.internal.dto.request.data

import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseData

internal data class InitRequestData(
    val installDate: Long,
    val idfa: String? = null,
    val purchases: List<PurchaseData>? = null,
    val callback: QonversionLaunchCallback? = null
)
