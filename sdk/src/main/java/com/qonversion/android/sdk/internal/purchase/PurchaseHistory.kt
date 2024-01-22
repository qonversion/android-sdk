package com.qonversion.android.sdk.internal.purchase

import com.android.billingclient.api.PurchaseHistoryRecord
import com.qonversion.android.sdk.internal.dto.QStoreProductType

internal data class PurchaseHistory(
    val type: QStoreProductType,
    val historyRecord: PurchaseHistoryRecord
)
