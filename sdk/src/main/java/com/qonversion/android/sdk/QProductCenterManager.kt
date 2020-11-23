package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.util.Pair
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.billing.*
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QProduct
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.Logger

class QProductCenterManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private val logger: Logger
): QonversionBillingService.PurchasesListener {

    private var isLaunchingFinished: Boolean = false
    private var isProductsLoaded: Boolean = false

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchResult: QLaunchResult? = null
    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var permissionsCallbacks = mutableListOf<QonversionPermissionsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionPermissionsCallback>()

    private var installDate: Long = 0

    private var converter: PurchaseConverter<Pair<SkuDetails, Purchase>> =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())

    @Volatile
    lateinit var billingService: BillingService
        @Synchronized set
        @Synchronized get

    @Volatile
    lateinit var consumer: Consumer
        @Synchronized set
        @Synchronized get

    init{
        installDate = context.packageManager.getPackageInfo(
            context.packageName,
            0
        ).firstInstallTime.milliSecondsToSeconds()
    }

    // Public functions

    fun onAppForeground() {
        handlePendingPurchases()
    }

    fun launch(
        callback: QonversionLaunchCallback?
    ) {
        val adProvider = AdvertisingProvider()
        val launchCallback: QonversionLaunchCallback = getLaunchCallback(callback)

        adProvider.init(context, object : AdvertisingProvider.Callback {
            override fun onSuccess(advertisingId: String) {
                continueLaunchWithPurchasesInfo(advertisingId, launchCallback)
            }

            override fun onFailure(t: Throwable) {
                continueLaunchWithPurchasesInfo(callback = launchCallback)
            }
        })
    }

    fun loadProducts(
        callback: QonversionProductsCallback
    ) {
        productsCallbacks.add(callback)
        if (!isProductsLoaded || !isLaunchingFinished) {
            return
        }

        if (launchError == null) {
            executeProductsBlocks()
        } else {
            val launchCallback = getLaunchCallback(null)
            launch(launchCallback)
        }
    }

    fun purchaseProduct(
        context: Activity,
        id: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionPermissionsCallback
    ) {
        val product: QProduct? = productForID(id)
        val oldProduct: QProduct? = productForID(oldProductId)

        if (product?.storeID == null) {
            callback.onError(QonversionError(QonversionErrorCode.ProductNotFound))
            return
        }

        val purchasingCallback = purchasingCallbacks[product.storeID]
        purchasingCallback?.let {
            logger.release("purchaseProduct() -> Purchase with id = $id is already in progress. This one call will be ignored")
            return
        }

        val skuDetail = skuDetails[product.storeID]
        val oldSkuDetail = skuDetails[oldProduct?.storeID]
        if (skuDetail != null) {
            purchasingCallbacks[product.storeID] = callback
            billingService.purchase(context, skuDetail, oldSkuDetail, prorationMode)
        } else {
            val launchResult = launchResult
            if (isProductsLoaded || launchResult == null) {
                val error = QonversionError(QonversionErrorCode.SkuDetailsError)
                callback.onError(error)
                return
            }

            loadStoreProductsIfPossible(
                launchResult,
                onLoadCompleted = { skuDetailsList ->
                    val sku = skuDetailsList.find { detail ->
                        detail.sku == product.storeID
                    }
                    if (sku != null) {
                        purchasingCallbacks[product.storeID] = callback
                        billingService.purchase(context, sku)
                    }
                }, onLoadFailed = { error ->
                    callback.onError(error.toQonversionError())
                })
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
            consumer.consumeHistoryRecords(historyRecords)
            val purchaseHistoryRecords = historyRecords.map { it.historyRecord }
            repository.restore(
                installDate,
                purchaseHistoryRecords,
                callback = object : QonversionPermissionsCallback {
                    override fun onSuccess(permissions: Map<String, QPermission>) {
                        launchResult?.permissions = permissions
                        callback?.onSuccess(permissions)
                    }

                    override fun onError(error: QonversionError) {
                        callback?.onError(error)
                    }
                })
        },
            onQueryHistoryFailed = {
                callback?.onError(it.toQonversionError())
            })
    }

    fun syncPurchases() {
        restore()
    }

    override fun onPurchasesCompleted(purchases: List<Purchase>) {
        handlePurchases(purchases)
    }

    override fun onPurchasesFailed(purchases: List<Purchase>, error: BillingError) {
        if (purchases.isNotEmpty()) {
            purchases.forEach { purchase ->
                val purchaseCallback = purchasingCallbacks[purchase.sku]
                purchasingCallbacks.remove(purchase.sku)
                purchaseCallback?.onError(error.toQonversionError())
            }
        } else {
            purchasingCallbacks.values.forEach {
                it.onError(error.toQonversionError())
            }
            purchasingCallbacks.clear()
        }
    }

    // Private functions

    private fun configurePurchaseInfo(
        skuDetails: Map<String, SkuDetails>,
        purchases: List<Purchase>
    ): List<com.qonversion.android.sdk.entity.Purchase> {
        val result = mutableListOf<com.qonversion.android.sdk.entity.Purchase>()

        purchases.forEach {
            val skuDetail = skuDetails[it.sku]
            if (skuDetail != null) {
                val purchaseInfo = Pair.create(skuDetail, it)
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

    private fun continueLaunchWithPurchasesInfo(
        advertisingId: String? = null,
        callback: QonversionLaunchCallback?
    ) {
        billingService.queryPurchases(
            onQueryCompleted = { purchases ->
                if (purchases.isEmpty()) {
                    repository.init(
                        installDate = installDate,
                        idfa = advertisingId,
                        callback = callback
                    )
                    return@queryPurchases
                }

                billingService.getSkuDetailsFromPurchases(
                    purchases,
                    onCompleted = { skuDetails ->
                        val formattedSkuDetails: Map<String, SkuDetails> =
                            configureSkuDetails(skuDetails)
                        val purchasesInfo = configurePurchaseInfo(formattedSkuDetails, purchases)
                        repository.init(installDate, advertisingId, purchasesInfo, callback)
                    },
                    onFailed = {
                        repository.init(
                            installDate = installDate,
                            idfa = advertisingId,
                            callback = callback
                        )
                    })
            },
            onQueryFailed = {
                repository.init(
                    installDate = installDate,
                    idfa = advertisingId,
                    callback = callback
                )
            })
    }

    private fun getLaunchCallback(callback: QonversionLaunchCallback?): QonversionLaunchCallback {
        return object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                isLaunchingFinished = true
                this@QProductCenterManager.launchResult = launchResult
                launchError = null

                loadStoreProductsIfPossible(launchResult)

                executePermissionsBlock()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                isLaunchingFinished = true
                launchResult = null
                launchError = error

                executePermissionsBlock()

                callback?.onError(error)
            }
        }
    }

    private fun loadStoreProductsIfPossible(launchResult: QLaunchResult) {
        loadStoreProductsIfPossible(launchResult, null, null)
    }

    private fun loadStoreProductsIfPossible(
        launchResult: QLaunchResult,
        onLoadCompleted: ((products: List<SkuDetails>) -> Unit)?,
        onLoadFailed: ((error: BillingError) -> Unit)?
    ) {
        val productStoreIds = launchResult.products.values.mapNotNull {
            it.storeID
        }.toSet()
        if (!isProductsLoaded && !productStoreIds.isNullOrEmpty()) {
            billingService.loadProducts(productStoreIds,
                onLoadCompleted = { details ->
                    isProductsLoaded = true
                    val formattedDetails: Map<String, SkuDetails> = configureSkuDetails(details)
                    skuDetails = formattedDetails.toMutableMap()

                    executeProductsBlocks()

                    onLoadCompleted?.let { it(details) }
                },
                onLoadFailed = { error ->
                    onLoadFailed?.let { it(error) }
                })
        }
    }

    private fun executeProductsBlocks() {
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
        if (permissionsCallbacks.isEmpty()) {
            return
        }

        launchResult?.let {
            val permissions = launchResult?.permissions ?: mapOf()
            permissionsCallbacks.forEach {
                it.onSuccess(permissions)
            }
        } ?: run {
            permissionsCallbacks.forEach {
                val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
                it.onError(error)
            }
        }

        permissionsCallbacks.clear()
    }

    private fun productForID(id: String?): QProduct? {
        return launchResult?.products?.get(id)
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
        consumer.consumePurchases(purchases, skuDetails)

        purchases.forEach { purchase ->
            val skuDetail = skuDetails[purchase.sku] ?: return@forEach

            val purchaseInfo = Pair.create(skuDetail, purchase)
            purchase(purchaseInfo, object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    launchResult?.permissions = permissions
                    val purchaseCallback = purchasingCallbacks[purchase.sku]
                    purchasingCallbacks.remove(purchase.sku)
                    purchaseCallback?.onSuccess(permissions)
                }

                override fun onError(error: QonversionError) {
                    val purchaseCallback = purchasingCallbacks[purchase.sku]
                    purchasingCallbacks.remove(purchase.sku)
                    purchaseCallback?.onError(error)
                }
            })
        }
    }

    private fun purchase(
        purchaseInfo: Pair<SkuDetails, Purchase>,
        callback: QonversionPermissionsCallback
    ) {
        val purchase = converter.convert(purchaseInfo)
        repository.purchase(installDate, purchase, callback)
    }
}