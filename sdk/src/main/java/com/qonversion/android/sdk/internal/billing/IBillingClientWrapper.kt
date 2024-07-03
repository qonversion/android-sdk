package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType

internal interface IBillingClientWrapper<in StoreId, out StoreData> {

    fun withStoreDataLoaded(
        storeIds: List<StoreId>,
        onFailed: (error: BillingError) -> Unit,
        onReady: () -> Unit,
    )

    fun getStoreData(storeId: StoreId): StoreData?

    fun makePurchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        applyOffer: Boolean,
        updatePurchaseInfo: UpdatePurchaseInfo?,
        onFailed: (error: BillingError) -> Unit
    )

    fun queryPurchaseForProduct(
        product: QProduct,
        onCompleted: (BillingResult, Purchase?) -> Unit
    )

    fun queryPurchases(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<Purchase>) -> Unit
    )

    fun consume(purchaseToken: String)

    fun acknowledge(purchaseToken: String)

    fun getStoreProductType(
        storeId: String,
        onFailed: (error: BillingError) -> Unit,
        onSuccess: (type: QStoreProductType) -> Unit
    )
}
