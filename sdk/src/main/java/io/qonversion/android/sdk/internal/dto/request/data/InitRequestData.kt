package io.qonversion.android.sdk.internal.dto.request.data

import io.qonversion.android.sdk.listeners.QonversionLaunchCallback
import io.qonversion.android.sdk.internal.purchase.Purchase

internal data class InitRequestData(
    val installDate: Long,
    val idfa: String? = null,
    val purchases: List<Purchase>? = null,
    val callback: QonversionLaunchCallback? = null
)
