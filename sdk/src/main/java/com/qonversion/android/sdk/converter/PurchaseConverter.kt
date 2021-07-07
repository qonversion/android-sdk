package com.qonversion.android.sdk.converter

import com.qonversion.android.sdk.entity.Purchase

interface PurchaseConverter<F> {
    fun convert(purchaseInfo: F) : Purchase?
    fun convert(purchaseInfo: List<F>) : List<Purchase>
}
