package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.util.Pair
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.billing.BillingError
import com.qonversion.android.sdk.billing.BillingService
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QProduct
import com.qonversion.android.sdk.entity.PurchaseHistory
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.Logger

class QProductCenterManager internal constructor(
    private var context: Application,
    private var isObserveMode: Boolean,
    private var repository: QonversionRepository,
    private var logger: Logger
) {
    @Volatile
    private var isLaunchingFinished: Boolean = false
        @Synchronized set
        @Synchronized get

    @Volatile
    private var isProductsLoaded: Boolean = false
        @Synchronized set
        @Synchronized get

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchResult: QLaunchResult? = null
    private var launchError: QLaunchResult? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var permissionsCallbacks = mutableListOf<QonversionPermissionsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionPermissionsCallback>()

    private var installDate: Long = 0

    private val listener: QonversionBillingService.PurchasesListener = getPurchasesListener()

    private val billingService: BillingService = QonversionBillingService(QonversionBillingService.BillingBuilder(context), Handler(context.mainLooper), listener, logger)

    private var converter: PurchaseConverter<Pair<SkuDetails, Purchase>> = GooglePurchaseConverter(SkuDetailsTokenExtractor())

    // Private functions

    private fun getPurchasesListener(): QonversionBillingService.PurchasesListener {
        return object : QonversionBillingService.PurchasesListener {
            override fun onPurchasesCompleted(purchases: List<Purchase>) {
                handlePurchases(purchases)
            }

            override fun onPurchasesFailed(purchases: List<Purchase>, error: BillingError) {
                purchases.forEach { purchase ->
                    val purchaseCallback = purchasingCallbacks[purchase.sku]
//                    purchaseCallback.onError(error)
                }
            }
        }
    }

    private fun configurePurchaseInfo(skuDetails: Map<String, SkuDetails>, purchases: List<Purchase>): List<com.qonversion.android.sdk.entity.Purchase> {
        val result = mutableListOf<com.qonversion.android.sdk.entity.Purchase>()

        purchases.forEach {
            val skuDetail = skuDetails[it.sku]
            if (skuDetail != null) {
                val purchaseInfo = android.util.Pair.create(skuDetail, it)
                val purchase = converter.convert(purchaseInfo)
                result.add(purchase)
            }
        }

        return result
    }

    private fun configureSkuDetails(skuDetails: List<SkuDetails>): Map<String, SkuDetails> {
        val formattedData = mutableMapOf<String, SkuDetails>()
        skuDetails.forEach {
            formattedData[it.sku] = it
        }

        return formattedData
    }

    private fun getInstallDate(): Long {
        if (installDate > 0) {
            return installDate
        }

        val date = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime / 1000
        installDate = date

        return installDate
    }

    private fun continueLaunchWithPurchasesInfo(context: Application, advertisingId: String? = null, callback: QonversionLaunchCallback?) {
        val installDate = getInstallDate()
        billingService.queryPurchases(
            onQueryCompleted = { purchases ->
                if (purchases.isEmpty()) {
                    repository.init(installDate = installDate, idfa = advertisingId, callback = callback)
                    return@queryPurchases
                }

                billingService.getSkuDetailsFromPurchases(
                    purchases,
                    onCompleted = { skuDetails ->
                        val formattedSkuDetails: Map<String, SkuDetails> = configureSkuDetails(skuDetails)
                        val purchasesInfo = configurePurchaseInfo(formattedSkuDetails, purchases)
                        repository.init(installDate, advertisingId, purchasesInfo, callback)
                    },
                    onFailed = {
                        repository.init(installDate = installDate, idfa = advertisingId, callback = callback)
                    })
            },
            onQueryFailed = {
                repository.init(installDate = installDate, idfa = advertisingId, callback = callback)
            })
    }

    private fun getLaunchCallback(callback: QonversionLaunchCallback?): QonversionLaunchCallback {
        return object: QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                isLaunchingFinished = true
                this@QProductCenterManager.launchResult = launchResult

                loadStoreProductsIfPossible(launchResult)

                executePermissionsBlock()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                isLaunchingFinished = true
                executePermissionsBlock()
            }
        }
    }

    private fun loadStoreProductsIfPossible(launchResult: QLaunchResult) {
        loadStoreProductsIfPossible(launchResult, null, null)
    }

    private fun loadStoreProductsIfPossible(launchResult: QLaunchResult,
                                    onLoadCompleted: ((products: List<SkuDetails>) -> Unit)?,
                                    onLoadFailed: ((error: BillingError) -> Unit)?) {
        val productStoreIds = launchResult.products.values.mapNotNull {
            if (!it.storeID.isEmpty()) {
                return@mapNotNull it.storeID
            } else {
                null
            }
        }.toSet()
        if (!isProductsLoaded && !productStoreIds.isNullOrEmpty()) {
            billingService.loadProducts(productStoreIds,
                onLoadCompleted = { details ->
                    isProductsLoaded = true
                    val formattedDetails: Map<String, SkuDetails> = configureSkuDetails(details)
                    skuDetails = formattedDetails.toMutableMap()

                    executeProductsBlocks()
                },
                onLoadFailed = {

                })
        }
    }

    private fun executeProductsBlocks() {
        executeProductsBlocks(null)
    }

    private fun executeProductsBlocks(error: String?) {
        if (productsCallbacks.isEmpty()) {
            return
        }

        launchResult?.products?.let { products ->
            products.values.forEach { product ->
                product.skuDetail = skuDetails[product.storeID]
            }

            productsCallbacks.forEach {
                it.onSuccess(products)
            }

            productsCallbacks.clear()
        }
    }

    private fun executePermissionsBlock() {
        launchResult?.let {
            val permissions = launchResult?.permissions ?: mapOf<String, QPermission>()
            permissionsCallbacks.forEach {
                it.onSuccess(permissions)
            }
        } ?: launchError.let {
            permissionsCallbacks.forEach {
                it.onError(error("lala"))
            }
        }

        permissionsCallbacks.clear()
    }

    private fun productForID(id: String): QProduct? {
        return launchResult?.products?.get(id)
    }

    private fun consumePurchases(purchases: List<Purchase>) {
        if (isObserveMode) {
            return
        }

        purchases.forEach {purchase ->
            val skuDetail = skuDetails[purchase.sku]
            skuDetail?.let { sku ->
                if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    return
                }
                consume(sku.type, purchase.purchaseToken, purchase.isAcknowledged)
            }
        }
    }

    private fun consume(type: String, purchaseToken: String, isAcknowledged: Boolean) {
        if (type == BillingClient.SkuType.INAPP) {
            billingService.consume(purchaseToken, onConsumeFailed = {

            })
        } else if (type == BillingClient.SkuType.SUBS && !isAcknowledged) {
            billingService.acknowledge(purchaseToken, onAcknowledgeFailed = {

            })
        }
    }

    private fun consumeHistoryRecords(records: List<PurchaseHistory>) {
        if (isObserveMode) {
            return
        }

        records.forEach { record ->
            consume(record.type, record.historyRecord.purchaseToken, false)
        }
    }

    private fun handlePendingPurchases() {
        if (!isLaunchingFinished) return

        billingService.queryPurchases(
            onQueryCompleted = { purchases ->
                handlePurchases(purchases)
            },
            onQueryFailed = {

            }
        )
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        consumePurchases(purchases)

        purchases.forEach { purchase ->
            val skuDetail = skuDetails[purchase.sku] ?: return@forEach

            val purchaseInfo = android.util.Pair.create(skuDetail, purchase)
            purchase(purchaseInfo, object: QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    val purchaseCallback = purchasingCallbacks[purchase.sku]
                    purchaseCallback?.onSuccess(permissions)
                }

                override fun onError(error: QonversionError) {
                    val purchaseCallback = purchasingCallbacks[purchase.sku]
                    purchaseCallback?.let {

                    }
                }

            })
        }
    }

    private fun purchase(
        purchaseInfo: android.util.Pair<SkuDetails, Purchase>,
        callback: QonversionPermissionsCallback
    ) {
        val purchase = converter.convert(purchaseInfo)
        val installDate = getInstallDate()
        repository.purchase(installDate, purchase, callback)
    }

    // Public functions

    fun onAppForegrounded() {
        handlePendingPurchases()
    }

    fun launch(
        context: Application,
        callback: QonversionLaunchCallback?
    ) {
        val adProvider = AdvertisingProvider()
        val launchCallback: QonversionLaunchCallback = getLaunchCallback(callback)

        adProvider.init(context, object : AdvertisingProvider.Callback {
            override fun onSuccess(advertisingId: String) {
                continueLaunchWithPurchasesInfo(context, advertisingId, launchCallback)
            }

            override fun onFailure(t: Throwable) {
                continueLaunchWithPurchasesInfo(context, callback = launchCallback)
            }
        })
    }

    fun loadProducts(
        callback: QonversionProductsCallback
    ) {
        if (!isProductsLoaded || !isLaunchingFinished) {
            productsCallbacks.add(callback)

            return
        }

        if (launchError == null) {
            executeProductsBlocks()
        } else {
            val launchCallback = getLaunchCallback(null)
            launch(context, launchCallback)
        }
    }

    fun purchaseProduct(
        id: String,
        context: Activity,
        callback: QonversionPermissionsCallback
    ) {
        val product: QProduct? = productForID(id)
        if (product == null) {
            callback.onError(error("lala"))
            return
        }

        val purchasingCallback = purchasingCallbacks[product.storeID]
        purchasingCallback?.let {
            // purchasing in progress
            return
        }

        purchasingCallbacks[product.storeID] = callback

        val skuDetail = skuDetails[product.storeID]
        skuDetail?.let {
            billingService.purchase(context, skuDetail)
        } ?: run {
            launchResult?.let {
                loadStoreProductsIfPossible(it,
                    onLoadCompleted = {
                        val skuDetail = it.find { it.sku == product.storeID }
                        if (skuDetail != null) {
                            billingService.purchase(context, skuDetail)
                        }
                    }, onLoadFailed = {billingError->
                        callback.onError(billingError.toQonversionError())
                    })
            } ?: run {
                callback.onError(error("lala"))
            }
        }
    }

    fun checkPermissions(
        callback: QonversionPermissionsCallback
    ) {
        permissionsCallbacks.add(callback)

        if (!isLaunchingFinished) {
            return
        }

        executePermissionsBlock()
    }

    fun restore(callback: QonversionPermissionsCallback? = null) {
        billingService.queryPurchasesHistory(onQueryHistoryCompleted = { historyRecords ->
            consumeHistoryRecords(historyRecords)
            val purchaseHistoryRecords = historyRecords.map { it.historyRecord }
            repository.restore(installDate, purchaseHistoryRecords, callback = object: QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    callback?.onSuccess(permissions)
                }

                override fun onError(error: QonversionError) {
                    callback?.onError(error)
                }
            })
        },
            onQueryHistoryFailed = {

            })
    }

    fun syncPurchases() {
        restore()
    }
}