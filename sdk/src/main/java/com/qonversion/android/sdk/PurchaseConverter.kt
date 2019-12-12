package com.qonversion.android.sdk

import com.qonversion.android.sdk.entity.Purchase

interface PurchaseConverter<F> {
    fun convert(purchaseInfo: F) : Purchase
}
