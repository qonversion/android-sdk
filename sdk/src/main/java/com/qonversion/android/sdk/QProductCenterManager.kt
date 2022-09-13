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
import com.qonversion.android.sdk.dto.QEntitlementCacheLifetime
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermissionsCacheLifetime
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
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
        get() = launchError != null || launchResultCache.sessionLaunchResult != null

    private var loadProductsState = NotStartedYet

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var experimentsCallbacks = mutableListOf<QonversionExperimentsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionPermissionsCallback>()

    private var processingPartnersIdentityId: String? = null
    private var pendingPartnersIdentityId: String? = null
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
        if (processingPartnersIdentityId == userID || identityManager.currentPartnersIdentityId == userID) {
            return
        }

        unhandledLogoutAvailable = false

        pendingPartnersIdentityId = userID
        if (!isLaunchingFinished) {
            return
        }

        processingPartnersIdentityId = userID

        if (launchError != null) {
            val callback = object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    processIdentity(userID)
                }

                override fun onError(error: QonversionError) {
                    processingPartnersIdentityId = null

                    entitlementsManager.onIdentityFailedWithError(userID, error)
                }
            }

            val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
            repository.init(initRequestData)
        } else {
            processIdentity(userID)
        }
    }

    private fun processIdentity(userId: String) {
        identityManager.identify(userId, object : IdentityManagerCallback {
            override fun onSuccess(qonversionUserId: String) {
                pendingPartnersIdentityId = null
                processingPartnersIdentityId = null

                config.setUid(qonversionUserId)
                entitlementsManager.onIdentitySucceeded(qonversionUserId, userId, !isLaunchingFinished)
            }

            override fun onError(error: QonversionError) {
                processingPartnersIdentityId = null

                entitlementsManager.onIdentityFailedWithError(userId, error)
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

        if (launchResultCache.sessionLaunchResult != null) {
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
        val offerings = getOfferings()

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

    private fun getOfferings(): QOfferings? {
        return launchResultCache.getLaunchResult()?.offerings
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
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            callback.onError(launchError ?: QonversionError(QonversionErrorCode.LaunchError))
            return
        }

        val product: QProduct? = getProductForPurchase(productId, offeringId, launchResult)
        val oldProduct: QProduct? = launchResult.products[oldProductId]

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
        launchResult: QLaunchResult
    ): QProduct? {
        if (productId == null) {
            return null
        }

        val product: QProduct?
        if (offeringId != null) {
            val offering = getOfferings()?.offeringForID(offeringId)
            product = offering?.productForID(productId)
            if (product != null && offering != null) {
                product.storeID?.let {
                    productPurchaseModel[it] = Pair(product, offering)
                }
            }
        } else {
            product = launchResult.products[productId]
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
            pendingPartnersIdentityId?.let {
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
                val permissions = entitlements.toPermissionsMap()
                callback?.onSuccess(permissions)
            }

            override fun onError(error: QonversionError, responseCode: Int?) {
                if (responseCode == HTTP_NOT_FOUND) {
                    if (launchError != null) {
                        retryLaunch(
                            onSuccess = { requestEntitlements(callback, ignoreCache) },
                            onError = { repeatedError -> callback?.onError(repeatedError) }
                        )
                    } else {
                        handleNewUserEntitlements(callback)
                    }
                } else {
                    callback?.onError(error)
                }
            }
        }
        entitlementsManager.checkEntitlements(
            config.uid,
            pendingPartnersIdentityId,
            entitlementsCallback,
            !isLaunchingFinished,
            ignoreCache
        )
    }

    private fun handleNewUserEntitlements(callback: QonversionPermissionsCallback? = null) {
        callback?.onSuccess(emptyMap())
        if (isLaunchingFinished) {
            handleLogout()
        }
    }

    fun restore(callback: QonversionPermissionsCallback? = null) {
        val qonversionUserId = config.uid
        billingService.queryPurchasesHistory(onQueryHistoryCompleted = { historyRecords ->
            consumer.consumeHistoryRecords(historyRecords)
            val skuIds = historyRecords.mapNotNull { it.historyRecord.sku }
            val loadedSkuDetails = skuDetails.filter { skuIds.contains(it.value.sku) }.toMutableMap()
            val resultSkuIds = (skuIds - loadedSkuDetails.keys).toSet()

            if (resultSkuIds.isNotEmpty()) {
                billingService.loadProducts(resultSkuIds, onLoadCompleted = {
                    it.forEach { singleSkuDetails -> run {
                        loadedSkuDetails[singleSkuDetails.sku] = singleSkuDetails
                        skuDetails = skuDetails + (singleSkuDetails.sku to singleSkuDetails)
                    } }

                    processRestore(qonversionUserId, historyRecords, loadedSkuDetails, callback)
                }, onLoadFailed = {
                    processRestore(qonversionUserId, historyRecords, loadedSkuDetails, callback)
                })
            } else {
                processRestore(qonversionUserId, historyRecords, loadedSkuDetails, callback)
            }
        },
            onQueryHistoryFailed = {
                callback?.onError(it.toQonversionError())
            })
    }

    fun syncPurchases() {
        restore()
    }

    fun setPermissionsCacheLifetime(lifetime: QPermissionsCacheLifetime) {
        entitlementsManager.setCacheLifetime(QEntitlementCacheLifetime.from(lifetime))
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
        qonversionUserId: String,
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
            object : QonversionLaunchCallbackInternal {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)

                    checkPermissionsAfterRestore(qonversionUserId, purchaseHistoryRecords, callback)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, httpCode)) {
                        calculateRestorePermissionsLocally(
                            qonversionUserId,
                            purchaseHistoryRecords,
                            callback,
                            error
                        )
                    } else {
                        callback?.onError(error)
                    }
                }
            })
    }

    private fun checkPermissionsAfterRestore(
        qonversionUserId: String,
        purchaseHistoryRecords: List<PurchaseHistory>,
        purchaseCallback: QonversionPermissionsCallback?
    ) {
        if (purchaseCallback != null) {
            val entitlementsCallback = object : QonversionEntitlementsCallbackInternal {
                override fun onSuccess(entitlements: List<QEntitlement>) {
                    val permissions = entitlements.toPermissionsMap()
                    purchaseCallback.onSuccess(permissions)
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, responseCode)) {
                        calculateRestorePermissionsLocally(
                            qonversionUserId,
                            purchaseHistoryRecords,
                            purchaseCallback,
                            error
                        )
                    } else {
                        purchaseCallback.onError(error)
                    }
                }
            }

            entitlementsManager.checkEntitlements(
                qonversionUserId,
                null,
                entitlementsCallback,
                !isLaunchingFinished,
                true
            )
        }
    }

    private fun calculateRestorePermissionsLocally(
        qonversionUserId: String,
        purchaseHistoryRecords: List<PurchaseHistory>,
        callback: QonversionPermissionsCallback?,
        restoreError: QonversionError
    ) {
        calculatePermissionsLocally(callback, restoreError) { productPermissions, products ->
            return@calculatePermissionsLocally entitlementsManager.grantEntitlementsAfterFailedRestore(
                qonversionUserId,
                purchaseHistoryRecords,
                products,
                productPermissions
            )
        }
    }

    private fun checkPermissionsAfterPurchase(
        qonversionUserId: String,
        purchase: Purchase,
        purchaseCallback: QonversionPermissionsCallback?,
        updatedPurchasesListener: UpdatedPurchasesListener?
    ) {
        if (purchaseCallback != null || updatedPurchasesListener != null) {
            val entitlementsCallback = object : QonversionEntitlementsCallbackInternal {
                override fun onSuccess(entitlements: List<QEntitlement>) {
                    val permissions = entitlements.toPermissionsMap()
                    purchaseCallback?.onSuccess(permissions) ?: run {
                        updatedPurchasesListener?.onPermissionsUpdate(permissions)
                    }
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, responseCode)) {
                        calculatePurchasePermissionsLocally(
                            qonversionUserId,
                            purchase,
                            purchaseCallback,
                            error
                        )
                    } else {
                        purchaseCallback?.onError(error)
                    }
                }
            }

            entitlementsManager.checkEntitlements(
                qonversionUserId,
                null,
                entitlementsCallback,
                !isLaunchingFinished,
                true
            )
        }
    }

    private fun calculatePurchasePermissionsLocally(
        qonversionUserId: String,
        purchase: Purchase,
        callback: QonversionPermissionsCallback?,
        purchaseError: QonversionError
    ) {
        calculatePermissionsLocally(callback, purchaseError) { productPermissions, products ->
            val purchasedProduct = products.find { it.skuDetail?.sku == purchase.sku } ?: run {
                failLocallyGrantingPermissionsWithError(callback, purchaseError)
                return@calculatePermissionsLocally null
            }

            return@calculatePermissionsLocally entitlementsManager.grantEntitlementsAfterFailedPurchaseTracking(
                qonversionUserId,
                purchase,
                purchasedProduct,
                productPermissions
            )
        }
    }

    private fun calculatePermissionsLocally(
        callback: QonversionPermissionsCallback?,
        purchaseError: QonversionError,
        grantEntitlements: (productPermissions: Map<String, List<String>>, products: Collection<QProduct>) -> List<QEntitlement>?
    ) {
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            failLocallyGrantingPermissionsWithError(
                callback,
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        launchResultCache.productPermissions?.let {
            if (launchResult.products.values.all { it.skuDetail == null }) {
                addSkuDetailForProducts(launchResult.products.values)
            }

            val entitlements = grantEntitlements(it, launchResult.products.values) ?: run {
                failLocallyGrantingPermissionsWithError(callback, purchaseError)
                return
            }

            callback?.onSuccess(entitlements.toPermissionsMap())
        } ?: failLocallyGrantingPermissionsWithError(callback, purchaseError)
    }

    private fun failLocallyGrantingPermissionsWithError(
        callback: QonversionPermissionsCallback?,
        error: QonversionError
    ) {
        entitlementsManager.resetCache()
        callback?.onError(error)
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
                        val skuDetailsMap = skuDetails.associateBy { it.sku }
                        val purchasesInfo = converter.convertPurchases(skuDetailsMap, completedPurchases)

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

                if (processingPartnersIdentityId == null) {
                    val pendingIdentityId = pendingPartnersIdentityId
                    if (!pendingIdentityId.isNullOrEmpty()) {
                        identify(pendingIdentityId)
                    } else if (unhandledLogoutAvailable) {
                        handleLogout()
                    } else {
                        entitlementsManager.onLaunchSucceeded(launchResult.uid)
                    }
                }

                loadStoreProductsIfPossible()

                executeExperimentsBlocks()
                handleCachedPurchases()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                launchError = error

                loadStoreProductsIfPossible()

                callback?.onError(error)
            }
        }
    }

    fun logout() {
        pendingPartnersIdentityId = null
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

        val launchResult = launchResultCache.getLaunchResult() ?: run {
            loadProductsState = Failed
            val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            executeProductsBlocks(error)
            onLoadFailed?.let { it(error) }
            return
        }

        val productStoreIds = launchResult.products.values.mapNotNull {
            it.storeID
        }.toSet()

        if (productStoreIds.isNotEmpty()) {
            loadProductsState = Loading
            billingService.loadProducts(productStoreIds,
                onLoadCompleted = { details ->
                    val skuDetailsMap = details.associateBy { it.sku }
                    skuDetails = skuDetailsMap.toMutableMap()

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
            repository.purchase(installDate, purchase, null, null, object : QonversionLaunchCallbackInternal {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    purchasesCache.clearPurchase(purchase)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {}
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

        launchResultCache.sessionLaunchResult?.experiments?.let { experiments ->
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

        val launchResult = launchResultCache.getLaunchResult() ?: run {
            handleFailureProducts(callbacks, launchError)
            return
        }

        addSkuDetailForProducts(launchResult.products.values)

        callbacks.forEach {
            it.onSuccess(launchResult.products)
        }
    }

    private fun retryLaunchForProducts(onCompleted: () -> Unit) {
        launchResultCache.sessionLaunchResult?.let {
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
        val qonversionUserId = config.uid

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
            purchase(purchaseInfo, object : QonversionLaunchCallbackInternal {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)

                    checkPermissionsAfterPurchase(
                        qonversionUserId,
                        purchase,
                        purchaseCallback,
                        listener
                    )

                    handledPurchasesCache.saveHandledPurchase(purchase)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, httpCode)) {
                        calculatePurchasePermissionsLocally(
                            qonversionUserId,
                            purchase,
                            purchaseCallback,
                            error
                        )
                    } else {
                        purchaseCallback?.onError(error)
                    }
                }
            })
        }
    }

    private fun purchase(
        purchaseInfo: Pair<SkuDetails, Purchase>,
        callback: QonversionLaunchCallbackInternal
    ) {
        val sku = purchaseInfo.first.sku
        val product = productPurchaseModel[sku]?.first
        val offering = productPurchaseModel[sku]?.second

        val purchase = converter.convertPurchase(purchaseInfo) ?: run {
            callback.onError(
                QonversionError(
                    QonversionErrorCode.ProductUnavailable,
                    "There is no SKU for the qonversion product ${product?.qonversionID ?: ""}"
                ),
                null
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

    private fun shouldCalculatePermissionsLocally(error: QonversionError, httpCode: Int?): Boolean {
        return !config.isObserveMode && (
                error.code == QonversionErrorCode.NetworkConnectionFailed ||
                        httpCode?.isInternalServerError() == true
                )
    }
}
