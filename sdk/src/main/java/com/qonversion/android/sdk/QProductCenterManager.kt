package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.util.Pair
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.ad.AdvertisingProvider
import com.qonversion.android.sdk.ad.LoadStoreProductsState.*
import com.qonversion.android.sdk.billing.*
import com.qonversion.android.sdk.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.converter.PurchaseConverter
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QRestoreResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.request.data.InitRequestData
import com.qonversion.android.sdk.entity.PurchaseHistory
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.services.QUserInfoService
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.storage.PurchasesCache
import java.net.HttpURLConnection.HTTP_NOT_FOUND

@SuppressWarnings("LongParameterList")
class QProductCenterManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val handledPurchasesCache: QHandledPurchasesCache,
    private val launchResultCache: LaunchResultCacheWrapper,
    private val userInfoService: QUserInfoService,
    private val identityManager: QIdentityManager,
    private val entitlementsManager: EntitlementsManager,
    private val config: QonversionConfig
) : QonversionBillingService.PurchasesListener, OfferingsDelegate {

    private var listener: UpdatedPurchasesListener? = null
    private val isLaunchingFinished: Boolean
        get() = launchError != null || launchResult != null

    private var loadProductsState = NotStartedYet

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchResult: QLaunchResult? = null
    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var experimentsCallbacks = mutableListOf<QonversionExperimentsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionPermissionsCallback>()

    private var identityInProgress: Boolean = false
    private var pendingIdentityUserID: String? = null
    private var unhandledLogoutAvailable: Boolean = false

    private var installDate: Long = 0
    private var advertisingID: String? = null
    private var pendingInitRequestData: InitRequestData? = null

    private var converter: PurchaseConverter<Pair<SkuDetails, Purchase>> =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())

    private var productPurchaseModel = mutableMapOf<String, Pair<QProduct, QOffering>>()

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

    override fun offeringByIDWasCalled(offering: QOffering?) {
        val isAttached = offering?.experimentInfo?.attached
        if (isAttached != null && !isAttached) {
            repository.experimentEvents(offering)
            offering.experimentInfo.attached = true
        }
    }

    fun onAppForeground() {
        handlePendingPurchases()

        processPendingInitIfAvailable()
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
                advertisingID = advertisingId
                continueLaunchWithPurchasesInfo(launchCallback)
            }

            override fun onFailure(t: Throwable) {
                continueLaunchWithPurchasesInfo(launchCallback)
            }
        })
    }

    fun loadProducts(
        callback: QonversionProductsCallback
    ) {
        productsCallbacks.add(callback)
        val isProductsLoaded = loadProductsState in listOf(Loaded, Failed)
        if (!isProductsLoaded || !isLaunchingFinished) {
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

    fun identify(userID: String) {
        if (identityManager.currentCustomUserId == userID) {
            return
        }

        pendingIdentityUserID = userID
        if (!isLaunchingFinished) {
            return
        }

        identityInProgress = true

        if (launchError != null || unhandledLogoutAvailable) {
            val callback = object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    unhandledLogoutAvailable = false
                    processIdentity(userID)
                }

                override fun onError(error: QonversionError) {
                    pendingIdentityUserID = null
                    identityInProgress = false

                    entitlementsManager.onIdentityFailedWithError(userID, error)
                }
            }

            val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
            repository.init(initRequestData)
        } else {
            processIdentity(userID)
        }
    }

    private fun processIdentity(userID: String) {
        identityManager.identify(userID, object : IdentityManagerCallback {
            override fun onSuccess(qonversionUserId: String) {
                pendingIdentityUserID = null
                identityInProgress = false

                config.setUid(qonversionUserId)
                entitlementsManager.checkEntitlementsAfterIdentity(qonversionUserId, userID)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                pendingIdentityUserID = null
                identityInProgress = false

                entitlementsManager.onIdentityFailedWithError(userID, error)
            }
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
                offering.observer = null
                addSkuDetailForProducts(offering.products)
                offering.observer = this
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
        product: QProduct,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionPermissionsCallback
    ) {
        purchaseProduct(context, product.qonversionID, oldProductId, prorationMode, product.offeringID, callback)
    }

    fun purchaseProduct(
        context: Activity,
        productId: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        offeringId: String?,
        callback: QonversionPermissionsCallback
    ) {
        if (launchError != null) {
            retryLaunch(
                onSuccess = {
                    tryToPurchase(context, productId, oldProductId, prorationMode, offeringId, callback)
                },
                onError = {
                    tryToPurchase(context, productId, oldProductId, prorationMode, offeringId, callback)
                }
            )
        } else {
            tryToPurchase(context, productId, oldProductId, prorationMode, offeringId, callback)
        }
    }

    private fun tryToPurchase(
        context: Activity,
        id: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        offeringId: String?,
        callback: QonversionPermissionsCallback
    ) {
        when (loadProductsState) {
            Loading, NotStartedYet -> {
                productsCallbacks.add(object : QonversionProductsCallback {
                    override fun onSuccess(products: Map<String, QProduct>) =
                        processPurchase(context, id, oldProductId, prorationMode, offeringId, callback)

                    override fun onError(error: QonversionError) = callback.onError(error)
                })
            }
            Loaded, Failed -> {
                processPurchase(context, id, oldProductId, prorationMode, offeringId, callback)
            }
        }
    }

    private fun processPurchase(
        context: Activity,
        productId: String,
        oldProductId: String?,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        offeringId: String?,
        callback: QonversionPermissionsCallback
    ) {
        val actualLaunchResult = getActualLaunchResult()
        if (actualLaunchResult == null) {
            callback.onError(launchError ?: QonversionError(QonversionErrorCode.LaunchError))
            return
        }

        val product: QProduct? = getProductForPurchase(productId, offeringId, actualLaunchResult)
        val oldProduct: QProduct? = actualLaunchResult.products[oldProductId]

        if (product?.storeID == null) {
            callback.onError(QonversionError(QonversionErrorCode.ProductNotFound))
            return
        }

        val purchasingCallback = purchasingCallbacks[product.storeID]
        purchasingCallback?.let {
            logger.release(
                "purchaseProduct() -> Purchase with id = " +
                        "$productId is already in progress. This one call will be ignored"
            )
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

    private fun getProductForPurchase(
        productId: String?,
        offeringId: String?,
        actualLaunchResult: QLaunchResult
    ): QProduct? {
        if (productId == null) {
            return null
        }

        val product: QProduct?
        if (offeringId != null) {
            val offering = getActualOfferings()?.offeringForID(offeringId)
            product = offering?.productForID(productId)
            if (product != null && offering != null) {
                product.storeID?.let {
                    productPurchaseModel[it] = Pair(product, offering)
                }
            }
        } else {
            product = actualLaunchResult.products[productId]
        }

        return product
    }

    fun checkPermissions(
        callback: QonversionPermissionsCallback,
        ignoreCache: Boolean = false
    ) {
        if (unhandledLogoutAvailable) {
            handleNewUserEntitlements(callback)
        } else {
            pendingIdentityUserID?.takeIf { !identityInProgress }?.let {
                identify(it)
            }
            requestEntitlements(callback, ignoreCache)
        }
    }

    private fun requestEntitlements(
        callback: QonversionPermissionsCallback? = null,
        ignoreCache: Boolean = false
    ) {
        val entitlementsCallback = object : QonversionEntitlementsCallbackInternal {
            override fun onSuccess(entitlements: List<QEntitlement>) {
                val permissions = entitlements.associate { it.permissionID to it.toPermission() }
                callback?.onSuccess(permissions)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                if (responseCode == HTTP_NOT_FOUND) {
                    handleNewUserEntitlements(callback)
                } else {
                    callback?.onError(error)
                }
            }
        }
        entitlementsManager.checkEntitlements(
            config.uid,
            pendingIdentityUserID,
            entitlementsCallback,
            ignoreCache
        )
    }

    private fun handleNewUserEntitlements(callback: QonversionPermissionsCallback? = null) {
        callback?.onSuccess(emptyMap())
        if (isLaunchingFinished) {
            handleLogout()
        }
    }

    private fun checkPermissionsAfterPurchase(
        purchaseCallback: QonversionPermissionsCallback?,
        updatedPurchasesListener: UpdatedPurchasesListener?
    ) {
        if (purchaseCallback != null || updatedPurchasesListener != null) {
            val permissionsCallback = object : QonversionPermissionsCallback {
                override fun onSuccess(permissions: Map<String, QPermission>) {
                    purchaseCallback?.onSuccess(permissions) ?: run {
                        updatedPurchasesListener?.onPermissionsUpdate(permissions)
                    }
                }

                override fun onError(error: QonversionError) {
                    purchaseCallback?.onError(error)
                }
            }
            checkPermissions(permissionsCallback, true)
        }
    }

    fun restore(callback: QonversionPermissionsCallback? = null) {
        billingService.queryPurchasesHistory(onQueryHistoryCompleted = { historyRecords ->
            consumer.consumeHistoryRecords(historyRecords)
            val skuIds = historyRecords.mapNotNull { it.historyRecord.sku }
            val loadedSkuDetails = skuDetails.filter { skuIds.contains(it.value.sku) }.toMutableMap()
            val resultSkuIds = (skuIds - loadedSkuDetails.keys).toSet()

            if (resultSkuIds.isNotEmpty()) {
                billingService.loadProducts(resultSkuIds, onLoadCompleted = {
                    it.forEach { skuDetails -> loadedSkuDetails[skuDetails.sku] = skuDetails }

                    processRestore(historyRecords, loadedSkuDetails, callback)
                }, onLoadFailed = {
                    processRestore(historyRecords, loadedSkuDetails, callback)
                })
            } else {
                processRestore(historyRecords, loadedSkuDetails, callback)
            }
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

    private fun processRestore(
        purchaseHistoryRecords: List<PurchaseHistory>,
        loadedSkuDetails: Map<String, SkuDetails>,
        callback: QonversionPermissionsCallback? = null
    ) {
        purchaseHistoryRecords.forEach { purchaseHistory ->
            val skuDetails = loadedSkuDetails[purchaseHistory.historyRecord.sku]
            purchaseHistory.skuDetails = skuDetails
        }

        repository.restore(
            installDate,
            purchaseHistoryRecords,
            object : QonversionRestoreCallback {
                override fun onSuccess(restoreResult: QRestoreResult) {
                    updateLaunchResult(restoreResult.toLaunchResult())
                    callback?.onSuccess(restoreResult.permissions)

                    val entitlements =
                        restoreResult.permissions.values.map { QEntitlement(it) }
                    entitlementsManager.onRestore(restoreResult.uid, entitlements)
                }

                override fun onError(error: QonversionError) {
                    callback?.onError(error)
                }
            })
    }

    private fun configureSkuDetails(skuDetails: List<SkuDetails>): Map<String, SkuDetails> {
        val formattedData = mutableMapOf<String, SkuDetails>()
        skuDetails.forEach {
            formattedData[it.sku] = it
        }

        return formattedData
    }

    private fun processPendingInitIfAvailable() {
        pendingInitRequestData?.let {
            processInit(it)
            pendingInitRequestData = null
        }
    }

    private fun processInit(initRequestData: InitRequestData) {
        if (Qonversion.appState.isBackground()) {
            pendingInitRequestData = initRequestData
            return
        }

        repository.init(initRequestData)
    }

    private fun continueLaunchWithPurchasesInfo(
        callback: QonversionLaunchCallback?
    ) {
        billingService.queryPurchases(
            onQueryCompleted = { purchases ->
                if (purchases.isEmpty()) {
                    val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
                    processInit(initRequestData)
                    return@queryPurchases
                }

                val completedPurchases =
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                billingService.getSkuDetailsFromPurchases(
                    completedPurchases,
                    onCompleted = { skuDetails ->
                        val formattedSkuDetails: Map<String, SkuDetails> =
                            configureSkuDetails(skuDetails)
                        val purchasesInfo = converter.convertPurchases(formattedSkuDetails, completedPurchases)

                        val handledPurchasesCallback = getWrappedPurchasesCallback(completedPurchases, callback)

                        val initRequestData = InitRequestData(
                            installDate,
                            advertisingID,
                            purchasesInfo,
                            handledPurchasesCallback
                        )
                        processInit(initRequestData)
                    },
                    onFailed = {
                        val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
                        processInit(initRequestData)
                    })
            },
            onQueryFailed = {
                val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
                processInit(initRequestData)
            })
    }

    private fun getWrappedPurchasesCallback(
        trackingPurchases: List<Purchase>,
        outerCallback: QonversionLaunchCallback?
    ): QonversionLaunchCallback {
        return object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                handledPurchasesCache.saveHandledPurchases(trackingPurchases)
                outerCallback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                outerCallback?.onError(error)
            }
        }
    }

    private fun getLaunchCallback(callback: QonversionLaunchCallback?): QonversionLaunchCallback {
        return object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                updateLaunchResult(launchResult)
                launchError = null

                if (!identityInProgress) {
                    val userID = pendingIdentityUserID
                    if (!userID.isNullOrEmpty()) {
                        identify(userID)
                    } else if (unhandledLogoutAvailable) {
                        handleLogout()
                    }
                }

                loadStoreProductsIfPossible()

                executeExperimentsBlocks()
                handleCachedPurchases()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                launchResult = null
                launchError = error

                loadStoreProductsIfPossible()

                callback?.onError(error)
            }
        }
    }

    fun logout() {
        pendingIdentityUserID = null
        val isLogoutNeeded = identityManager.logoutIfNeeded()

        if (isLogoutNeeded) {
            unhandledLogoutAvailable = true

            val userID = userInfoService.obtainUserID()
            config.setUid(userID)
        }
    }

    private fun handleLogout() {
        unhandledLogoutAvailable = false
        launch()
    }

    private fun updateLaunchResult(launchResult: QLaunchResult) {
        this@QProductCenterManager.launchResult = launchResult
        launchResultCache.save(launchResult)
    }

    private fun loadStoreProductsIfPossible(
        onLoadCompleted: ((products: List<SkuDetails>) -> Unit)? = null,
        onLoadFailed: ((error: QonversionError) -> Unit)? = null
    ) {
        when (loadProductsState) {
            Loading -> return
            Loaded -> {
                executeProductsBlocks()
                onLoadCompleted?.let { skuDetails.values.toList() }
                return
            }
            else -> Unit
        }

        val actualLaunchResult = getActualLaunchResult() ?: run {
            loadProductsState = Failed
            val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            executeProductsBlocks(error)
            onLoadFailed?.let { it(error) }
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
            repository.purchase(installDate, purchase, null, null, object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    purchasesCache.clearPurchase(purchase)
                }

                override fun onError(error: QonversionError) {}
            })
        }
    }

    @Synchronized
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

    @Synchronized
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

        val actualLaunchResult = getActualLaunchResult()

        if (actualLaunchResult == null) {
            handleFailureProducts(callbacks, launchError)
        } else {
            addSkuDetailForProducts(actualLaunchResult.products.values)

            callbacks.forEach {
                it.onSuccess(actualLaunchResult.products)
            }
        }
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

    private fun handleFailureProducts(
        callbacks: List<QonversionProductsCallback>,
        error: QonversionError?
    ) {
        callbacks.forEach {
            it.onError(error ?: QonversionError(QonversionErrorCode.LaunchError))
        }
    }

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

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    purchaseCallback?.onError(QonversionError(QonversionErrorCode.PurchasePending))
                    return@forEach
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    purchaseCallback?.onError(QonversionError(QonversionErrorCode.PurchaseUnspecified))
                    return@forEach
                }
            }

            if (!handledPurchasesCache.shouldHandlePurchase(purchase)) return@forEach
            val skuDetail = skuDetails[purchase.sku] ?: return@forEach

            val purchaseInfo = Pair.create(skuDetail, purchase)
            purchase(purchaseInfo, object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)

                    checkPermissionsAfterPurchase(purchaseCallback, listener)
                    handledPurchasesCache.saveHandledPurchase(purchase)
                }

                override fun onError(error: QonversionError) {
                    purchaseCallback?.onError(error)
                }
            })
        }
    }

    private fun purchase(
        purchaseInfo: Pair<SkuDetails, Purchase>,
        callback: QonversionLaunchCallback
    ) {
        val sku = purchaseInfo.first.sku
        val product = productPurchaseModel[sku]?.first
        val offering = productPurchaseModel[sku]?.second

        val purchase = converter.convertPurchase(purchaseInfo) ?: run {
            callback.onError(
                QonversionError(
                    QonversionErrorCode.ProductUnavailable,
                    "There is no SKU for the qonversion product ${product?.qonversionID ?: ""}"
                )
            )
            return
        }

        if (sku == product?.storeID) {
            repository.purchase(installDate, purchase, offering?.experimentInfo, product?.qonversionID, callback)
            productPurchaseModel.remove(sku)
        } else {
            repository.purchase(installDate, purchase, null, product?.qonversionID, callback)
        }
    }
}
