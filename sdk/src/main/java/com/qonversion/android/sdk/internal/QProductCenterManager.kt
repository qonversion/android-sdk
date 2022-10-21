package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.util.Pair
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionErrorCode
import com.qonversion.android.sdk.listeners.QonversionExperimentsCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallbackInternal
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionPermissionsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.UpdatedPurchasesListener
import com.qonversion.android.sdk.internal.LoadStoreProductsState.NotStartedYet
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Loaded
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Failed
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Loading
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.converter.GoogleBillingPeriodConverter
import com.qonversion.android.sdk.internal.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.internal.converter.PurchaseConverter
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QPermissionSource
import com.qonversion.android.sdk.dto.QPermissionsCacheLifetime
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOffering
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductRenewState
import com.qonversion.android.sdk.internal.billing.BillingError
import com.qonversion.android.sdk.internal.billing.BillingService
import com.qonversion.android.sdk.internal.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.billing.sku
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.internal.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import java.util.Date

@SuppressWarnings("LongParameterList")
internal class QProductCenterManager internal constructor(
    private val context: Application,
    private val repository: QonversionRepository,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val handledPurchasesCache: QHandledPurchasesCache,
    private val launchResultCache: LaunchResultCacheWrapper,
    private val userInfoService: QUserInfoService,
    private val identityManager: QIdentityManager,
    private val config: QonversionConfig
) : QonversionBillingService.PurchasesListener, OfferingsDelegate {

    private var listener: UpdatedPurchasesListener? = null
    private val isLaunchingFinished: Boolean
        get() = launchError != null || launchResultCache.sessionLaunchResult != null

    private var loadProductsState = NotStartedYet

    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var permissionsCallbacks = mutableListOf<QonversionPermissionsCallback>()
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

        launchResultCache.clearPermissionsCache()
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

                    executePermissionsBlock(error)
                }
            }

            val initRequestData = InitRequestData(installDate, advertisingID, callback = callback)
            repository.init(initRequestData)
        } else {
            processIdentity(userID)
        }
    }

    private fun processIdentity(userID: String) {
        val currentUserID = userInfoService.obtainUserID()

        identityManager.identify(userID, object : IdentityManagerCallback {
            override fun onSuccess(identityID: String) {
                pendingPartnersIdentityId = null
                processingPartnersIdentityId = null

                if (currentUserID == identityID) {
                    executePermissionsBlock()
                } else {
                    config.uid = identityID

                    launch()
                }
            }

            override fun onError(error: QonversionError) {
                processingPartnersIdentityId = null

                executePermissionsBlock(error)
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
        callback: QonversionPermissionsCallback
    ) {
        permissionsCallbacks.add(callback)

        if (!isLaunchingFinished || processingPartnersIdentityId != null) {
            return
        }

        val pendingIdentityID = pendingPartnersIdentityId
        if (!pendingIdentityID.isNullOrEmpty()) {
            identify(pendingIdentityID)
            return
        }

        executePermissionsBlock()
    }

    fun restore(callback: QonversionPermissionsCallback? = null) {
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

    fun setPermissionsCacheLifetime(lifetime: QPermissionsCacheLifetime) {
        launchResultCache.setPermissionsCacheLifetime(lifetime)
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
            object : QonversionLaunchCallbackInternal {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    callback?.onSuccess(launchResult.permissions)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, httpCode)) {
                        calculateRestorePermissionsLocally(purchaseHistoryRecords, callback, error)
                    } else {
                        callback?.onError(error)
                    }
                }
            })
    }

    private fun calculateRestorePermissionsLocally(
        purchaseHistoryRecords: List<PurchaseHistory>,
        callback: QonversionPermissionsCallback?,
        restoreError: QonversionError
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

            val permissions = grantPermissionsAfterFailedRestore(
                purchaseHistoryRecords,
                launchResult.products.values,
                it
            )

            callback?.onSuccess(permissions)
        } ?: failLocallyGrantingPermissionsWithError(callback, restoreError)
    }

    private fun calculatePurchasePermissionsLocally(
        purchase: Purchase,
        purchaseCallback: QonversionPermissionsCallback?,
        purchaseError: QonversionError
    ) {
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            failLocallyGrantingPermissionsWithError(
                purchaseCallback,
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        launchResultCache.productPermissions?.let {
            if (launchResult.products.values.all { it.skuDetail == null }) {
                addSkuDetailForProducts(launchResult.products.values)
            }

            val purchasedProduct = launchResult.products.values.find { it.skuDetail?.sku == purchase.sku } ?: run {
                failLocallyGrantingPermissionsWithError(purchaseCallback, purchaseError)
                return
            }

            val permissions = grantPermissionsAfterFailedPurchaseTracking(
                purchase,
                purchasedProduct,
                it
            )
            purchaseCallback?.onSuccess(permissions)
        } ?: failLocallyGrantingPermissionsWithError(purchaseCallback, purchaseError)
    }

    private fun failLocallyGrantingPermissionsWithError(
        callback: QonversionPermissionsCallback?,
        error: QonversionError
    ) {
        launchResultCache.clearPermissionsCache()
        callback?.onError(error)
    }

    private fun grantPermissionsAfterFailedPurchaseTracking(
        purchase: Purchase,
        purchasedProduct: QProduct,
        productPermissions: Map<String, List<String>>
    ): Map<String, QPermission> {
        val newPermissions = productPermissions[purchasedProduct.qonversionID]?.mapNotNull {
            createPermission(it, purchase.purchaseTime, purchasedProduct)
        } ?: emptyList()

        return mergeManuallyCreatedPermissions(newPermissions)
    }

    private fun grantPermissionsAfterFailedRestore(
        historyRecords: List<PurchaseHistory>,
        products: Collection<QProduct>,
        productPermissions: Map<String, List<String>>
    ): Map<String, QPermission> {
        val newPermissions = historyRecords
            .filter { it.skuDetails != null }
            .mapNotNull { record ->
                val product = products.find { it.skuDetail?.sku === record.skuDetails?.sku }
                product?.let {
                    productPermissions[product.qonversionID]?.map {
                        createPermission(it, record.historyRecord.purchaseTime, product)
                    }
                }
            }
            .flatten()
            .filterNotNull()

        return mergeManuallyCreatedPermissions(newPermissions)
    }

    private fun createPermission(id: String, purchaseTime: Long, purchasedProduct: QProduct): QPermission? {
        val purchaseDurationInDays = GoogleBillingPeriodConverter.convertPeriodToDays(
            purchasedProduct.skuDetail?.subscriptionPeriod
        )

        val expirationDate = purchaseDurationInDays?.let { Date(purchaseTime + it.daysToMs) }
        return if (expirationDate == null || Date() < expirationDate) {
            return QPermission(
                id,
                purchasedProduct.qonversionID,
                QProductRenewState.Unknown,
                Date(purchaseTime),
                expirationDate,
                QPermissionSource.PlayStore,
                1
            )
        } else null
    }

    private fun mergeManuallyCreatedPermissions(
        newPermissions: List<QPermission>
    ): Map<String, QPermission> {
        val existingPermissions = launchResultCache.getActualPermissions() ?: emptyMap()
        val resultPermissions = existingPermissions.toMutableMap()

        newPermissions.forEach { newPermission ->
            val id = newPermission.permissionID
            resultPermissions[id] = choosePermissionToSave(resultPermissions[id], newPermission)
        }

        launchResultCache.updatePermissions(resultPermissions)

        return resultPermissions
    }

    private fun choosePermissionToSave(
        existingPermission: QPermission?,
        localCreatedPermission: QPermission
    ): QPermission {
        existingPermission ?: return localCreatedPermission

        // If expiration date is null then it's permanent permissions and thus should take precedence over expiring one.
        val newPermissionExpirationTime = localCreatedPermission.expirationDate?.time ?: Long.MAX_VALUE
        val existingPermissionExpirationTime = existingPermission.expirationDate?.time ?: Long.MAX_VALUE
        val doesNewOneExpireLater =
            newPermissionExpirationTime > existingPermissionExpirationTime

        // replace if new permission is active and expires later
        return if (!existingPermission.isActive() || doesNewOneExpireLater) {
            localCreatedPermission
        } else {
            existingPermission
        }
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
                        executePermissionsBlock()
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
                executePermissionsBlock(error.takeIf { pendingPartnersIdentityId != null })

                callback?.onError(error)
            }
        }
    }

    fun logout() {
        pendingPartnersIdentityId = null
        val isLogoutNeeded = identityManager.logoutIfNeeded()

        if (isLogoutNeeded) {
            launchResultCache.clearPermissionsCache()

            unhandledLogoutAvailable = true

            val userID = userInfoService.obtainUserID()
            config.uid = userID
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
            repository.purchase(installDate, purchase, null, null, object :
                QonversionLaunchCallbackInternal {
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

    @Synchronized
    private fun executePermissionsBlock(error: QonversionError? = null) {
        if (permissionsCallbacks.isEmpty()) {
            return
        }

        val callbacks = permissionsCallbacks.toList()
        permissionsCallbacks.clear()

        error?.let {
            callbacks.forEach { it.onError(error) }
        } ?: run {
            preparePermissionsResult(
                { permissions ->
                    callbacks.forEach {
                        it.onSuccess(permissions)
                    }
                },
                { error ->
                    callbacks.forEach {
                        it.onError(error)
                    }
                })
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

    private fun preparePermissionsResult(
        onSuccess: (permissions: Map<String, QPermission>) -> Unit,
        onError: (QonversionError) -> Unit
    ) {
        if (launchError != null || unhandledLogoutAvailable) {
            retryLaunch(
                onSuccess = { launchResult ->
                    onSuccess(launchResult.permissions)
                    unhandledLogoutAvailable = false
                },
                onError = { error ->
                    val cachedPermissions = launchResultCache.getActualPermissions()

                    cachedPermissions?.let {
                        onSuccess(it)
                    } ?: onError(error)
                })
        } else {
            val permissions = launchResultCache.getActualPermissions() ?: emptyMap()
            onSuccess(permissions)
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

                    purchaseCallback?.onSuccess(launchResult.permissions) ?: run {
                        listener?.onPermissionsUpdate(launchResult.permissions)
                    }
                    handledPurchasesCache.saveHandledPurchase(purchase)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, httpCode)) {
                        calculatePurchasePermissionsLocally(
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
