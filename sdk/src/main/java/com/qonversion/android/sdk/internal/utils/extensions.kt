package com.qonversion.android.sdk.internal.utils

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord

internal val Purchase.sku: String? get() = skus.firstOrNull()

internal val PurchaseHistoryRecord.sku: String? get() = skus.firstOrNull()

private const val MS_IN_SEC = 1000
internal fun Long.msToSec(): Long = this / MS_IN_SEC
