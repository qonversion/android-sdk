package com.qonversion.android.sdk.internal.billing.controller

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.dto.PurchaseHistory
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface GoogleBillingController {

    @Throws(QonversionException::class)
    suspend fun queryPurchasesHistory(): List<PurchaseHistory>

    @Throws(QonversionException::class)
    suspend fun queryPurchases(): List<Purchase>

    @Throws(QonversionException::class)
    suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        oldSkuDetails: SkuDetails? = null,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null
    )

    @Throws(QonversionException::class)
    suspend fun loadProducts(productIds: Set<String>): List<SkuDetails>

    suspend fun consume(purchaseToken: String)

    suspend fun acknowledge(purchaseToken: String)

    @Throws(QonversionException::class)
    suspend fun getSkuDetailsFromPurchases(purchases: List<Purchase>): List<SkuDetails>
}
