package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Pair
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.*
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.internal.LoadStoreProductsState.NotStartedYet
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Loaded
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Failed
import com.qonversion.android.sdk.internal.LoadStoreProductsState.Loading
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.converter.GoogleBillingPeriodConverter
import com.qonversion.android.sdk.internal.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.internal.converter.PurchaseConverter
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.internal.billing.BillingError
import com.qonversion.android.sdk.internal.billing.BillingService
import com.qonversion.android.sdk.internal.billing.sku
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.extractor.SkuDetailsTokenExtractor
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.purchase.PurchaseHistory
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import java.util.Date

@SuppressWarnings("LongParameterList")
internal class QProductCenterManager internal constructor(
    private val context: Application,
    private val repository: QRepository,
    private val logger: Logger,
    private val purchasesCache: PurchasesCache,
    private val handledPurchasesCache: QHandledPurchasesCache,
    private val launchResultCache: LaunchResultCacheWrapper,
    private val userInfoService: QUserInfoService,
    private val identityManager: QIdentityManager,
    private val internalConfig: InternalConfig,
    private val appStateProvider: AppStateProvider,
    private val remoteConfigManager: QRemoteConfigManager
) : QonversionBillingService.PurchasesListener, UserStateProvider {

    override val isUserStable: Boolean
        get() = isLaunchingFinished &&
                processingPartnersIdentityId == null &&
                pendingPartnersIdentityId.isNullOrEmpty() &&
                !unhandledLogoutAvailable

    private val isLaunchingFinished: Boolean
        get() = launchError != null || launchResultCache.sessionLaunchResult != null

    private var isRestoreInProgress = false

    private var loadProductsState = NotStartedYet

    @Suppress("DEPRECATION")
    private var skuDetails = mapOf<String, SkuDetails>()

    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var entitlementCallbacks = mutableListOf<QonversionEntitlementsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionEntitlementsCallback>()
    private var restoreCallbacks = mutableListOf<QonversionEntitlementsCallback>()

    private var processingPartnersIdentityId: String? = null
    private var pendingPartnersIdentityId: String? = null
    private var unhandledLogoutAvailable: Boolean = false

    private var installDate: Long = 0
    private var advertisingID: String? = null
    private var pendingInitRequestData: InitRequestData? = null

    @Suppress("DEPRECATION")
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
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
        }
        installDate = packageInfo.firstInstallTime.milliSecondsToSeconds()
    }

    // Public functions

    fun onAppForeground() {
        handlePendingPurchases()

        processPendingInitIfAvailable()
    }

    fun launch(
        callback: QonversionLaunchCallback? = null
    ) {
        val launchCallback: QonversionLaunchCallback = getLaunchCallback(callback)
        launchError = null
        launchResultCache.resetSessionCache()

        if (!internalConfig.primaryConfig.isKidsMode) {
            val adProvider = AdvertisingProvider()
            adProvider.init(context, object : AdvertisingProvider.Callback {
                override fun onSuccess(advertisingId: String) {
                    advertisingID = advertisingId
                    continueLaunchWithPurchasesInfo(launchCallback)
                }

                override fun onFailure(t: Throwable) {
                    continueLaunchWithPurchasesInfo(launchCallback)
                }
            })
        } else {
            continueLaunchWithPurchasesInfo(launchCallback)
        }
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
        if (!isLaunchingFinished || isRestoreInProgress) {
            return
        }

        processingPartnersIdentityId = userID

        if (launchError != null) {
            val callback = object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    processIdentity(userID)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    processingPartnersIdentityId = null

                    remoteConfigManager.userChangingRequestFailedWithError(error)
                    executeEntitlementsBlock(error)
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
                    handlePendingRequests()
                } else {
                    internalConfig.uid = identityID
                    remoteConfigManager.onUserUpdate()
                    launchResultCache.clearPermissionsCache()
                    launch()
                }
            }

            override fun onError(error: QonversionError) {
                processingPartnersIdentityId = null

                executeEntitlementsBlock(error)
                remoteConfigManager.userChangingRequestFailedWithError(error)
            }
        })
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
                addSkuDetailForProducts(offering.products)
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
        @Suppress("DEPRECATION") @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        purchaseProduct(
            context,
            product.qonversionID,
            oldProductId,
            prorationMode,
            callback
        )
    }

    fun purchaseProduct(
        context: Activity,
        productId: String,
        oldProductId: String?,
        @Suppress("DEPRECATION") @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        if (launchError != null) {
            retryLaunch(
                onSuccess = {
                    tryToPurchase(
                        context,
                        productId,
                        oldProductId,
                        prorationMode,
                        callback
                    )
                },
                onError = {
                    tryToPurchase(
                        context,
                        productId,
                        oldProductId,
                        prorationMode,
                        callback
                    )
                }
            )
        } else {
            tryToPurchase(context, productId, oldProductId, prorationMode, callback)
        }
    }

    private fun tryToPurchase(
        context: Activity,
        id: String,
        oldProductId: String?,
        @Suppress("DEPRECATION") @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        when (loadProductsState) {
            Loading, NotStartedYet -> {
                productsCallbacks.add(object : QonversionProductsCallback {
                    override fun onSuccess(products: Map<String, QProduct>) =
                        processPurchase(
                            context,
                            id,
                            oldProductId,
                            prorationMode,
                            callback
                        )

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
        productId: String,
        oldProductId: String?,
        @Suppress("DEPRECATION") @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            callback.onError(launchError ?: QonversionError(QonversionErrorCode.LaunchError))
            return
        }

        val product: QProduct? = getProductForPurchase(productId, launchResult)
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
        launchResult: QLaunchResult
    ): QProduct? {
        if (productId == null) {
            return null
        }

        return launchResult.products[productId]
    }

    fun checkEntitlements(callback: QonversionEntitlementsCallback) {
        entitlementCallbacks.add(callback)

        handlePendingRequests()
    }

    fun restore(callback: QonversionEntitlementsCallback? = null) {
        callback?.let { restoreCallbacks.add(it) }

        if (isRestoreInProgress) {
            return
        }
        isRestoreInProgress = true

        billingService.queryPurchasesHistory(
            onQueryHistoryCompleted = { historyRecords ->
                consumer.consumeHistoryRecords(historyRecords)
                val skuIds = historyRecords.mapNotNull { it.historyRecord.sku }
                val loadedSkuDetails =
                    skuDetails.filter { skuIds.contains(it.value.sku) }.toMutableMap()
                val resultSkuIds = (skuIds - loadedSkuDetails.keys).toSet()

                if (resultSkuIds.isNotEmpty()) {
                    billingService.loadProducts(resultSkuIds, onLoadCompleted = {
                        it.forEach { singleSkuDetails ->
                            run {
                                loadedSkuDetails[singleSkuDetails.sku] = singleSkuDetails
                                skuDetails = skuDetails + (singleSkuDetails.sku to singleSkuDetails)
                            }
                        }

                        processRestore(historyRecords, loadedSkuDetails)
                    }, onLoadFailed = {
                        processRestore(historyRecords, loadedSkuDetails)
                    })
                } else {
                    processRestore(historyRecords, loadedSkuDetails)
                }
            },
            onQueryHistoryFailed = {
                executeRestoreBlocksOnError(it.toQonversionError())
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
        @Suppress("DEPRECATION") loadedSkuDetails: Map<String, SkuDetails>
    ) {
        purchaseHistoryRecords.forEach { purchaseHistory ->
            val skuDetails = loadedSkuDetails[purchaseHistory.historyRecord.sku]
            purchaseHistory.skuDetails = skuDetails
        }

        repository.restore(
            installDate,
            purchaseHistoryRecords,
            object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    executeRestoreBlocksOnSuccess(launchResult.permissions.toEntitlementsMap())
                }

                override fun onError(error: QonversionError, httpCode: Int?) {
                    if (shouldCalculatePermissionsLocally(error, httpCode)) {
                        calculateRestorePermissionsLocally(purchaseHistoryRecords, error)
                    } else {
                        executeRestoreBlocksOnError(error)
                    }
                }
            })
    }

    private fun calculateRestorePermissionsLocally(
        purchaseHistoryRecords: List<PurchaseHistory>,
        restoreError: QonversionError
    ) {
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            failLocallyGrantingRestorePermissionsWithError(
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        launchResultCache.productPermissions?.let {
            if (launchResult.products.values.all { product -> product.skuDetail == null }) {
                addSkuDetailForProducts(launchResult.products.values)
            }

            val permissions = grantPermissionsAfterFailedRestore(
                purchaseHistoryRecords,
                launchResult.products.values,
                it
            )

            executeRestoreBlocksOnSuccess(permissions.toEntitlementsMap())
        } ?: failLocallyGrantingRestorePermissionsWithError(restoreError)
    }

    private fun calculatePurchasePermissionsLocally(
        purchase: Purchase,
        purchaseCallback: QonversionEntitlementsCallback?,
        purchaseError: QonversionError
    ) {
        val launchResult = launchResultCache.getLaunchResult() ?: run {
            failLocallyGrantingPurchasePermissionsWithError(
                purchaseCallback,
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        launchResultCache.productPermissions?.let {
            if (launchResult.products.values.all { product -> product.skuDetail == null }) {
                addSkuDetailForProducts(launchResult.products.values)
            }

            val purchasedProduct = launchResult.products.values.find { product ->
                product.skuDetail?.sku == purchase.sku
            } ?: run {
                failLocallyGrantingPurchasePermissionsWithError(purchaseCallback, purchaseError)
                return
            }

            val permissions = grantPermissionsAfterFailedPurchaseTracking(
                purchase,
                purchasedProduct,
                it
            )
            purchaseCallback?.onSuccess(permissions.toEntitlementsMap())
        } ?: failLocallyGrantingPurchasePermissionsWithError(purchaseCallback, purchaseError)
    }

    private fun failLocallyGrantingPurchasePermissionsWithError(
        callback: QonversionEntitlementsCallback?,
        error: QonversionError
    ) {
        launchResultCache.clearPermissionsCache()
        callback?.onError(error)
    }

    private fun failLocallyGrantingRestorePermissionsWithError(
        error: QonversionError
    ) {
        launchResultCache.clearPermissionsCache()
        executeRestoreBlocksOnError(error)
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

    private fun createPermission(
        id: String,
        purchaseTime: Long,
        purchasedProduct: QProduct
    ): QPermission? {
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
                QEntitlementSource.PlayStore,
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
        val newPermissionExpirationTime =
            localCreatedPermission.expirationDate?.time ?: Long.MAX_VALUE
        val existingPermissionExpirationTime =
            existingPermission.expirationDate?.time ?: Long.MAX_VALUE
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
        if (appStateProvider.appState.isBackground()) {
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
                    val initRequestData =
                        InitRequestData(installDate, advertisingID, callback = callback)
                    processInit(initRequestData)
                    return@queryPurchases
                }

                val completedPurchases =
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                billingService.getSkuDetailsFromPurchases(
                    completedPurchases,
                    onCompleted = { skuDetails ->
                        val skuDetailsMap = skuDetails.associateBy { it.sku }
                        val purchasesInfo =
                            converter.convertPurchases(skuDetailsMap, completedPurchases)

                        val handledPurchasesCallback =
                            getWrappedPurchasesCallback(completedPurchases, callback)

                        val initRequestData = InitRequestData(
                            installDate,
                            advertisingID,
                            purchasesInfo,
                            handledPurchasesCallback
                        )
                        processInit(initRequestData)
                    },
                    onFailed = {
                        val initRequestData =
                            InitRequestData(installDate, advertisingID, callback = callback)
                        processInit(initRequestData)
                    })
            },
            onQueryFailed = {
                val initRequestData =
                    InitRequestData(installDate, advertisingID, callback = callback)
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

            override fun onError(error: QonversionError, httpCode: Int?) {
                outerCallback?.onError(error, httpCode)
            }
        }
    }

    private fun getLaunchCallback(callback: QonversionLaunchCallback?): QonversionLaunchCallback {
        return object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                updateLaunchResult(launchResult)

                launchError = null

                handlePendingRequests()

                loadStoreProductsIfPossible()

                handleCachedPurchases()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError, httpCode: Int?) {
                launchError = error

                handlePendingRequests(error)

                loadStoreProductsIfPossible()

                callback?.onError(error, httpCode)
            }
        }
    }

    fun logout() {
        pendingPartnersIdentityId = null
        val isLogoutNeeded = identityManager.logoutIfNeeded()

        if (isLogoutNeeded) {
            remoteConfigManager.onUserUpdate()
            launchResultCache.clearPermissionsCache()

            unhandledLogoutAvailable = true

            val userID = userInfoService.obtainUserID()
            internalConfig.uid = userID
        }
    }

    fun getUserInfo(callback: QonversionUserCallback) {
        val user = QUser(internalConfig.uid, identityManager.currentPartnersIdentityId)
        callback.onSuccess(user)
    }

    fun setEntitlementsUpdateListener(entitlementsUpdateListener: QEntitlementsUpdateListener) {
        internalConfig.entitlementsUpdateListener = entitlementsUpdateListener
    }

    private fun handleLogout() {
        unhandledLogoutAvailable = false
        launch()
    }

    private fun updateLaunchResult(launchResult: QLaunchResult) {
        launchResultCache.save(launchResult)
    }

    private fun loadStoreProductsIfPossible(
        @Suppress("DEPRECATION") onLoadCompleted: ((products: List<SkuDetails>) -> Unit)? = null,
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
            @Suppress("DEPRECATION")
            onLoadCompleted?.let { listOf<SkuDetails>() }
        }
    }

    /**
     * Executes identity changing operations (identify or logout) if pending requests exist.
     * Else executes awaiting entitlements requests.
     */
    private fun handlePendingRequests(lastError: QonversionError? = null) {
        if (!isLaunchingFinished || isRestoreInProgress || processingPartnersIdentityId != null) {
            return
        }

        val pendingIdentityId = pendingPartnersIdentityId
        if (!pendingIdentityId.isNullOrEmpty()) {
            identify(pendingIdentityId)
        } else if (unhandledLogoutAvailable) {
            handleLogout()
        } else {
            executeEntitlementsBlock(lastError)
            remoteConfigManager.handlePendingRequests()
        }
    }

    private fun handleCachedPurchases() {
        val cachedPurchases = purchasesCache.loadPurchases()
        cachedPurchases.forEach { purchase ->
            repository.purchase(installDate, purchase, null, object :
                QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    purchasesCache.clearPurchase(purchase)
                }

                override fun onError(error: QonversionError, httpCode: Int?) {}
            })
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
    private fun executeEntitlementsBlock(error: QonversionError? = null) {
        if (entitlementCallbacks.isEmpty()) {
            return
        }

        val callbacks = entitlementCallbacks.toList()
        entitlementCallbacks.clear()

        error?.let {
            callbacks.forEach { it.onError(error) }
        } ?: run {
            preparePermissionsResult(
                { permissions ->
                    callbacks.forEach {
                        it.onSuccess(permissions.toEntitlementsMap())
                    }
                },
                { error ->
                    callbacks.forEach {
                        it.onError(error)
                    }
                })
        }
    }

    private fun executeRestoreBlocksOnSuccess(entitlements: Map<String, QEntitlement>) {
        val callbacks = restoreCallbacks.toList()
        restoreCallbacks.clear()

        isRestoreInProgress = false

        callbacks.forEach { callback -> callback.onSuccess(entitlements) }

        handlePendingRequests()
    }

    private fun executeRestoreBlocksOnError(error: QonversionError) {
        val callbacks = restoreCallbacks.toList()
        restoreCallbacks.clear()

        isRestoreInProgress = false

        callbacks.forEach { callback -> callback.onError(error) }

        handlePendingRequests(error)
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
            override fun onError(error: QonversionError, httpCode: Int?) = onError(error)
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
            purchase(purchaseInfo, object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)

                    val entitlements = launchResult.permissions.toEntitlementsMap()

                    purchaseCallback?.onSuccess(entitlements) ?: run {
                        internalConfig.entitlementsUpdateListener?.onEntitlementsUpdated(entitlements)
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
        @Suppress("DEPRECATION") purchaseInfo: Pair<SkuDetails, Purchase>,
        callback: QonversionLaunchCallback
    ) {
        val sku = purchaseInfo.first.sku
        val product: QProduct? = launchResultCache.getLaunchResult()?.products?.values?.find { it.storeID == sku }
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

        repository.purchase(installDate, purchase, product?.qonversionID, callback)
    }

    private fun shouldCalculatePermissionsLocally(error: QonversionError, httpCode: Int?): Boolean {
        return !internalConfig.isAnalyticsMode && (
                error.code == QonversionErrorCode.NetworkConnectionFailed ||
                        httpCode?.isInternalServerError() == true
                )
    }
}
