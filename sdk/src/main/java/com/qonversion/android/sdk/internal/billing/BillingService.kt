package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.QPurchaseUpdatePolicy
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory

internal interface BillingService {

    fun enrichStoreDataAsync(
        products: List<QProduct>,
        onFailed: (error: BillingError) -> Unit,
        onEnriched: (products: List<QProduct>) -> Unit
    )

    fun enrichStoreData(products: List<QProduct>)

    fun purchase(
        activity: Activity,
        product: QProduct,
        offerId: String?,
        oldProduct: QProduct? = null,
        updatePolicy: QPurchaseUpdatePolicy? = null
    )

    fun consumePurchases(purchases: List<Purchase>, products: List<QProduct>)

    fun consumeHistoryRecords(historyRecords: List<PurchaseHistory>)

    fun queryPurchasesHistory(
        onFailed: (error: BillingError) -> Unit,
        onCompleted: (purchases: List<PurchaseHistory>) -> Unit
    )

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
