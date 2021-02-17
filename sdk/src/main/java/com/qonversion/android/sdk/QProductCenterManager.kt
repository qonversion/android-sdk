package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.util.Pair
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.ad.LoadProductsState.*
import com.qonversion.android.sdk.billing.*
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.storage.PurchasesCache

class QProductCenterManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val launchResultCache: LaunchResultCacheWrapper
) : QonversionBillingService.PurchasesListener {

    private var listener: UpdatedPurchasesListener? = null
    private val isLaunchingFinished: Boolean
        get() = launchError != null || launchResult != null

    private var loadProductsState = NotStartedYet

    private var forceLaunchRetry: Boolean = false

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchResult: QLaunchResult? = null
    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var permissionsCallbacks = mutableListOf<QonversionPermissionsCallback>()
    private var experimentsCallbacks = mutableListOf<QonversionExperimentsCallback>()
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

    init {
        installDate = context.packageManager.getPackageInfo(
            context.packageName,
            0
        ).firstInstallTime.milliSecondsToSeconds()
    }

    // Public functions

    fun onAppForeground() {
        handlePendingPurchases()
    }

    fun setUpdatedPurchasesListener(listener: UpdatedPurchasesListener) {
        this.listener = listener
    }

    fun launch(
        callback: QonversionLaunchCallback? = null
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
        if (loadProductsState in listOf(Loading, NotStartedYet) || !isLaunchingFinished) {
            return
        }

        retryLaunchForProducts { executeProductsBlocks() }
    }

    fun offerings(
        callback: QonversionOfferingsCallback
    ) {
        loadProducts(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) =
                executeOfferingCallback(callback)

            override fun onError(error: QonversionError) = callback.onError(error)
        })
    }

    fun experiments(
        callback: QonversionExperimentsCallback
    ) {
        experimentsCallbacks.add(callback)

        if (!isLaunchingFinished) {
            return
        }

        if (launchResult != null) {
            executeExperimentsBlocks()
        } else {
            launch()
        }
    }

    fun checkTrialIntroEligibilityForProductIds(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    ) {
        loadProducts(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val storeIds = products.map { it.value.skuDetail?.sku }.filterNotNull()
                repository.eligibilityForProductIds(
                    storeIds,
                    installDate,
                    object : QonversionEligibilityCallback {
                        override fun onSuccess(eligibilities: Map<String, QEligibility>) {
                            val resultForRequiredProductIds =
                                eligibilities.filter { it.key in productIds }
                            callback.onSuccess(resultForRequiredProductIds)
                        }

                        override fun onError(error: QonversionError) = callback.onError(error)
                    })
            }

            override fun onError(error: QonversionError) = callback.onError(error)
        })
    }

    private fun executeOfferingCallback(callback: QonversionOfferingsCallback) {
        val offerings = getActualOfferings()
        if (offerings != null) {
            offerings.availableOfferings.forEach { offering ->
                addSkuDetailForProducts(offering.products)
            }
            callback.onSuccess(offerings)
        } else {
            val error = launchError ?: QonversionError(QonversionErrorCode.OfferingsNotFound)
            callback.onError(error)
        }
    }

    private fun getActualOfferings(): QOfferings? {
        var offerings = launchResult?.offerings
        if (launchResult == null) {
            val cachedLaunchResult = launchResultCache.getActualLaunchResult()
            cachedLaunchResult?.let { offerings = it.offerings }
        }
        return offerings
    }

    private fun addSkuDetailForProducts(products: Collection<QProduct>) {
        products.forEach { product ->
            product.skuDetail = skuDetails[product.storeID]
        }
    }

    fun purchaseProduct(
        context: Activity,
        id: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionPermissionsCallback
    ) {
        if (launchError != null) {
            retryLaunch(
                onSuccess = {
                    tryToPurchase(context, id, oldProductId, prorationMode, callback)
                },
                onError = {
                    tryToPurchase(context, id, oldProductId, prorationMode, callback)
                }
            )
        } else {
            tryToPurchase(context, id, oldProductId, prorationMode, callback)
        }
    }

    private fun tryToPurchase(
        context: Activity,
        id: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionPermissionsCallback
    ) {
        when (loadProductsState) {
            Loading, NotStartedYet -> {
                productsCallbacks.add(object : QonversionProductsCallback {
                    override fun onSuccess(products: Map<String, QProduct>) =
                        processPurchase(context, id, oldProductId, prorationMode, callback)

                    override fun onError(error: QonversionError) = callback.onError(error)
                })
            }
            Loaded, Failed -> {
                processPurchase(context, id, oldProductId, prorationMode, callback)
            }
        }
    }

    private fun processPurchase(
        context: Activity,
        id: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionPermissionsCallback
    ) {
        val actualLaunchResult = getActualLaunchResult()
        if (actualLaunchResult == null) {
            callback.onError(launchError ?: QonversionError(QonversionErrorCode.LaunchError))
            return
        }

        val product: QProduct? = productForID(id, actualLaunchResult)
        val oldProduct: QProduct? = productForID(oldProductId, actualLaunchResult)

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
            if (loadProductsState == Loaded) {
                val error = QonversionError(QonversionErrorCode.SkuDetailsError)
                callback.onError(error)
                return
            }

            loadStoreProductsIfPossible(
                onLoadCompleted = { skuDetailsList ->
                    val sku = skuDetailsList.find { detail ->
                        detail.sku == product.storeID
                    }
                    if (sku != null) {
                        purchasingCallbacks[product.storeID] = callback
                        billingService.purchase(context, sku)
                    }
                }, onLoadFailed = { error ->
                    callback.onError(error)
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
                object : QonversionLaunchCallback {
                    override fun onSuccess(launchResult: QLaunchResult) {
                        updateLaunchResult(launchResult)
                        callback?.onSuccess(launchResult.permissions)
                    }

                    override fun onError(error: QonversionError) {
                        forceLaunchRetry = true
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
                updateLaunchResult(launchResult)
                launchError = null

                loadStoreProductsIfPossible()

                executePermissionsBlock()
                executeExperimentsBlocks()
                handleCachedPurchases()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                launchResult = null
                launchError = error

                loadStoreProductsIfPossible()
                executePermissionsBlock()

                callback?.onError(error)
            }
        }
    }

    private fun updateLaunchResult(launchResult: QLaunchResult) {
        this@QProductCenterManager.launchResult = launchResult
        launchResultCache.save(launchResult)
        forceLaunchRetry = false
    }

    private fun loadStoreProductsIfPossible(
        onLoadCompleted: ((products: List<SkuDetails>) -> Unit)? = null,
        onLoadFailed: ((error: QonversionError) -> Unit)? = null
    ) {
        when (loadProductsState) {
            Loading -> return
            Loaded -> {
                executeProductsBlocks()
                return
            }
            else -> Unit
        }

        val actualLaunchResult: QLaunchResult = getActualLaunchResult() ?: run {
            loadProductsState = Failed
            executeProductsBlocks(launchError ?: QonversionError(QonversionErrorCode.LaunchError))
            return
        }

        val productStoreIds = actualLaunchResult.products.values.mapNotNull {
            it.storeID
        }.toSet()

        if (productStoreIds.isNotEmpty()) {
            loadProductsState = Loading
            billingService.loadProducts(productStoreIds,
                onLoadCompleted = { details ->
                    val formattedDetails: Map<String, SkuDetails> = configureSkuDetails(details)
                    skuDetails = formattedDetails.toMutableMap()

                    loadProductsState = Loaded

                    executeProductsBlocks()

                    onLoadCompleted?.let { it(details) }
                },
                onLoadFailed = { error ->
                    loadProductsState = Failed
                    executeProductsBlocks(error.toQonversionError())
                    onLoadFailed?.let { it(error.toQonversionError()) }
                })
        } else {
            executeProductsBlocks()
            onLoadCompleted?.let { listOf<SkuDetails>() }
        }
    }

    private fun handleCachedPurchases() {
        val cachedPurchases = purchasesCache.loadPurchases()
        cachedPurchases.forEach { purchase ->
            repository.purchase(installDate, purchase, object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    purchasesCache.clearPurchase(purchase)
                }

                override fun onError(error: QonversionError) {}
            })
        }
    }

    private fun executeExperimentsBlocks() {
        if (experimentsCallbacks.isEmpty()) {
            return
        }

        val callbacks = experimentsCallbacks.toList()
        experimentsCallbacks.clear()

        launchResult?.experiments?.let { experiments ->
            callbacks.forEach {
                it.onSuccess(experiments)
            }
        } ?: run {
            experimentsCallbacks.forEach {
                val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
                it.onError(error)
            }
        }
    }

    private fun executeProductsBlocks(loadStoreProductsError: QonversionError? = null) {
        if (productsCallbacks.isEmpty()) {
            return
        }

        val callbacks = productsCallbacks.toList()
        productsCallbacks.clear()

        loadStoreProductsError?.let {
            handleFailureProducts(callbacks, it)
            return
        }

        val actualLaunchResult = launchResult ?: launchResultCache.getActualLaunchResult()

        if (actualLaunchResult == null) {
            handleFailureProducts(callbacks, launchError)
        } else {
            handleSuccessProducts(callbacks, actualLaunchResult.products)
        }
    }

    private fun executePermissionsBlock() {
        if (permissionsCallbacks.isEmpty()) {
            return
        }

        val callbacks = permissionsCallbacks.toList()
        permissionsCallbacks.clear()

        retryLaunchForPermissions(
            { permissions -> handleSuccessPermissions(callbacks, permissions) },
            { error -> handleFailurePermissions(callbacks, error) }
        )
    }

    private fun retryLaunchForProducts(onCompleted: () -> Unit) {
        launchResult?.let {
            handleLoadStateForProducts(onCompleted)
        } ?: retryLaunch(
            onSuccess = {
                handleLoadStateForProducts(onCompleted)
            },
            onError = {
                handleLoadStateForProducts(onCompleted)
            })
    }

    private fun retryLaunch(
        onSuccess: (QLaunchResult) -> Unit,
        onError: (QonversionError) -> Unit
    ) {
        launch(object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) = onSuccess(launchResult)
            override fun onError(error: QonversionError) = onError(error)
        })
    }

    private fun handleLoadStateForProducts(onCompleted: () -> Unit) {
        when (loadProductsState) {
            Loaded -> onCompleted()
            Failed -> loadStoreProductsIfPossible()
            else -> Unit
        }
    }

    private fun handleSuccessProducts(
        callbacks: List<QonversionProductsCallback>,
        products: Map<String, QProduct>
    ) {
        addSkuDetailForProducts(products.values)

        callbacks.forEach {
            it.onSuccess(products)
        }
    }

    private fun handleFailureProducts(
        callbacks: List<QonversionProductsCallback>,
        error: QonversionError?
    ) {
        callbacks.forEach {
            it.onError(error ?: QonversionError(QonversionErrorCode.LaunchError))
        }
    }

    private fun retryLaunchForPermissions(
        onSuccess: (permissions: Map<String, QPermission>) -> Unit,
        onError: (QonversionError) -> Unit
    ) {
        launchResult?.let {
            val permissions = launchResult?.permissions ?: mapOf()
            onSuccess(permissions)
        } ?: retryLaunch(
            onSuccess = { launchResult ->
                onSuccess(launchResult.permissions)
            },
            onError = { error ->
                if (forceLaunchRetry) {
                    onError(error)
                } else {
                    val cachedLaunchResult = launchResultCache.getActualLaunchResult()

                    cachedLaunchResult?.let {
                        onSuccess(it.permissions)
                    } ?: onError(error)
                }
            })
    }

    private fun handleSuccessPermissions(
        callbacks: List<QonversionPermissionsCallback>,
        permissions: Map<String, QPermission>
    ) {
        callbacks.forEach {
            it.onSuccess(permissions)
        }
    }

    private fun handleFailurePermissions(
        callbacks: List<QonversionPermissionsCallback>,
        error: QonversionError
    ) {
        callbacks.forEach {
            it.onError(error)
        }
    }

    private fun productForID(id: String?, launchResult: QLaunchResult): QProduct? =
        launchResult.products[id]

    private fun getActualLaunchResult(): QLaunchResult? =
        this@QProductCenterManager.launchResult ?: launchResultCache.getActualLaunchResult()

    private fun handlePendingPurchases() {
        if (!isLaunchingFinished) return

        billingService.queryPurchases(
            onQueryCompleted = { purchases ->
                handlePurchases(purchases)
            },
            onQueryFailed = {}
        )
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        consumer.consumePurchases(purchases, skuDetails)

        purchases.forEach { purchase ->
            val purchaseCallback = purchasingCallbacks[purchase.sku]
            purchasingCallbacks.remove(purchase.sku)

            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                purchaseCallback?.onError(QonversionError(QonversionErrorCode.PurchasePending))
                return@forEach
            }

            val skuDetail = skuDetails[purchase.sku] ?: return@forEach

            val purchaseInfo = Pair.create(skuDetail, purchase)
            purchase(purchaseInfo, object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)

                    purchaseCallback?.onSuccess(launchResult.permissions) ?: run {
                        listener?.onPermissionsUpdate(launchResult.permissions)
                    }
                }

                override fun onError(error: QonversionError) {
                    purchaseCallback?.onError(error)
                    forceLaunchRetry = true
                }
            })
        }
    }

    private fun purchase(
        purchaseInfo: Pair<SkuDetails, Purchase>,
        callback: QonversionLaunchCallback
    ) {
        val purchase = converter.convert(purchaseInfo)
        repository.purchase(installDate, purchase, callback)
    }
}