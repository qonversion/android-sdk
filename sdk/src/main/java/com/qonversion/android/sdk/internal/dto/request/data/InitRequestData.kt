package com.qonversion.android.sdk.internal.dto.request.data

import com.qonversion.android.sdk.internal.api.RequestTrigger
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.internal.dto.purchase.Purchase

internal data class InitRequestData(
    val installDate: Long,
    val idfa: String? = null,
    val purchases: List<Purchase>? = null,
    val callback: QonversionLaunchCallback? = null,
    val requestTrigger: RequestTrigger,
)
