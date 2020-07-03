package com.qonversion.android.sdk.storage.purchase

import com.qonversion.android.sdk.entity.Purchase

interface PurchaseDataSource {

    fun savePurchase(purchase: Purchase): Long

    fun isPurchaseExist(purchase: Purchase): Boolean

    fun count(): Int
}