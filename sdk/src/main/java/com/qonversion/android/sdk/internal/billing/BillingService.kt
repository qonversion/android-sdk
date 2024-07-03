package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseModelInternalEnriched

internal interface BillingService {

    fun enrichStoreDataAsync(
        products: List<QProduct>,
        onFailed: (error: BillingError) -> Unit,
        onEnriched: (products: List<QProduct>) -> Unit
    )

    fun enrichStoreData(products: List<QProduct>)

    fun purchase(
        activity: Activity,
        purchaseModel: PurchaseModelInternalEnriched,
    )

    fun consumePurchases(purchases: List<Purchase>)

    fun queryPurchases(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<Purchase>) -> Unit
    )

    fun getStoreProductType(
        storeId: String,
        onFailed: (error: BillingError) -> Unit,
        onSuccess: (type: QStoreProductType) -> Unit
    )
}
