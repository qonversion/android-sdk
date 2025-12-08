package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.internal.converter.PurchaseConverter
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.entitlements.QEntitlementGrantType
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductType
import com.qonversion.android.sdk.internal.api.RequestTrigger
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.internal.billing.BillingError
import com.qonversion.android.sdk.internal.billing.BillingService
import com.qonversion.android.sdk.internal.billing.productId
import com.qonversion.android.sdk.internal.dto.QStoreProductType
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseOptionsInternal
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseOptionsInternalEnriched
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseRecord
import com.qonversion.android.sdk.internal.dto.request.data.InitRequestData
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import com.qonversion.android.sdk.dto.QPurchaseResult
import kotlin.math.min
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

    private var launchError: QonversionError? = null

    private var productsCallbacks = mutableListOf<QonversionProductsCallback>()
    private var entitlementCallbacks = mutableListOf<QonversionEntitlementsCallback>()
    private var purchasingCallbacks = mutableMapOf<String, QonversionPurchaseCallback>()
    private var restoreCallbacks = mutableListOf<QonversionEntitlementsCallback>()

    private var processingPartnersIdentityId: String? = null
    private var pendingPartnersIdentityId: String? = null
    private var pendingIdentityCallbacks = mutableMapOf<String, MutableList<QonversionUserCallback>>()
    private var unhandledLogoutAvailable: Boolean = false

    private var installDate: Long = 0
    private var advertisingId: String? = null
    private var pendingInitRequestData: InitRequestData? = null

    private var processingPurchases: List<Purchase> = emptyList()

    private var converter: PurchaseConverter = GooglePurchaseConverter()

    private val processingPurchaseOptions: MutableMap<String, QPurchaseOptions> by lazy {
        purchasesCache.loadProcessingPurchasesOptions().toMutableMap()
    }

    @Volatile
    lateinit var billingService: BillingService
        @Synchronized set
        @Synchronized get

    init {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
        }
        installDate = packageInfo.firstInstallTime.milliSecondsToSeconds()
    }

    // Public functions

    fun onAppForeground() {
        handlePendingPurchases()

        processPendingInitIfAvailable()
    }

    fun launch(requestTrigger: RequestTrigger, callback: QonversionLaunchCallback? = null) {
        val launchCallback: QonversionLaunchCallback = getLaunchCallback(callback)
        launchError = null
        launchResultCache.resetSessionCache()

        if (!internalConfig.primaryConfig.isKidsMode) {
            val adProvider = AdvertisingProvider()
            adProvider.init(context, object : AdvertisingProvider.Callback {
                override fun onSuccess(advertisingId: String) {
                    this@QProductCenterManager.advertisingId = advertisingId
                    continueLaunchWithPurchasesInfo(launchCallback, requestTrigger)
                }

                override fun onFailure(t: Throwable) {
                    continueLaunchWithPurchasesInfo(launchCallback, requestTrigger)
                }
            })
        } else {
            continueLaunchWithPurchasesInfo(launchCallback, requestTrigger)
        }
    }

    fun loadProducts(callback: QonversionProductsCallback) {
        productsCallbacks.add(callback)
        if (!isLaunchingFinished) {
            return
        }

        launchResultCache.sessionLaunchResult?.let {
            loadStoreProductsIfPossible()
        } ?: launch(RequestTrigger.Products)
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

    fun identify(identityId: String, callback: QonversionUserCallback? = null) {
        if (identityManager.currentPartnersIdentityId == identityId) {
            callback?.let { getUserInfo(callback) }
            return
        }

        addIdentityCallback(identityId, callback)
        if (processingPartnersIdentityId == identityId) {
            return
        }

        unhandledLogoutAvailable = false

        pendingPartnersIdentityId = identityId
        if (!isLaunchingFinished || isRestoreInProgress) {
            return
        }

        processingPartnersIdentityId = identityId

        if (launchError != null) {
            val launchCallback = object : QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    processIdentity(identityId)
                }

                override fun onError(error: QonversionError) {
                    processingPartnersIdentityId = null

                    remoteConfigManager.userChangingRequestFailedWithError(error)
                    executeEntitlementsBlock(error)
                }
            }

            val initRequestData = InitRequestData(
                installDate,
                advertisingId,
                null,
                launchCallback,
                RequestTrigger.Identify
            )
            repository.init(initRequestData)
        } else {
            processIdentity(identityId)
        }
    }

    private fun processIdentity(identityId: String) {
        val currentUserId = userInfoService.obtainUserId()

        identityManager.identify(identityId, object : IdentityManagerCallback {
            override fun onSuccess(qonversionUid: String) {
                pendingPartnersIdentityId = null
                processingPartnersIdentityId = null

                if (currentUserId == qonversionUid) {
                    handlePendingRequests()
                    fireIdentitySuccess(identityId)
                } else {
                    internalConfig.uid = qonversionUid
                    remoteConfigManager.onUserUpdate()
                    launchResultCache.clearPermissionsCache()
                    launch(RequestTrigger.Identify, object : QonversionLaunchCallback {
                        override fun onSuccess(launchResult: QLaunchResult) {
                            fireIdentitySuccess(identityId)
                        }

                        override fun onError(error: QonversionError) {
                            fireIdentityError(identityId, error)
                        }
                    })
                }
            }

            override fun onError(error: QonversionError) {
                processingPartnersIdentityId = null

                executeEntitlementsBlock(error)
                remoteConfigManager.userChangingRequestFailedWithError(error)

                fireIdentityError(identityId, error)
            }
        })
    }

    fun checkTrialIntroEligibilityForProductIds(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    ) {
        loadProducts(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) {
                val result = products.filter { it.key in productIds }.mapValues {
                    val product = it.value

                    if (product.storeDetails?.isPrepaid == true) {
                        QEligibility(QIntroEligibilityStatus.NonIntroOrTrialProduct)
                    } else {
                        QEligibility(QIntroEligibilityStatus.fromProductType(product.type))
                    }
                }

                callback.onSuccess(result)
            }

            override fun onError(error: QonversionError) = callback.onError(error)
        })
    }

    private fun executeOfferingCallback(callback: QonversionOfferingsCallback) {
        val offerings = getOfferings()

        if (offerings != null) {
            offerings.availableOfferings.forEach { offering ->
                billingService.enrichStoreData(offering.products)
            }
            callback.onSuccess(offerings)
        } else {
            val error = launchError ?: QonversionError(QonversionErrorCode.OfferingsNotFound)
            callback.onError(error)
        }
    }

    private fun getOfferings(): QOfferings? {
        return launchResultCache.getActualOfferings()
    }

    fun purchaseProduct(
        context: Activity,
        purchaseOptions: PurchaseOptionsInternal,
        callback: QonversionPurchaseCallback
    ) {
        // route result callback via result path
        purchaseProductInternal(context, purchaseOptions, callback)
    }

    private fun purchaseProductInternal(
        context: Activity,
        purchaseOptions: PurchaseOptionsInternal,
        callback: QonversionPurchaseCallback
    ) {
        if (internalConfig.isAnalyticsMode) {
            logger.warn(
                "Making purchases via Qonversion in the Analytics mode can lead to " +
                        "an inconsistent state in the store. Consider switching to " +
                        "the Subscription management mode.")
        }

        fun tryToPurchase() {
            tryToPurchase(context, purchaseOptions, callback)
        }

        if (launchError != null) {
            retryLaunch(
                onSuccess = { tryToPurchase() },
                onError = { tryToPurchase() },
                requestTrigger = RequestTrigger.Purchase,
            )
        } else {
            tryToPurchase()
        }
    }

    private fun tryToPurchase(
        context: Activity,
        purchaseOptions: PurchaseOptionsInternal,
        callback: QonversionPurchaseCallback
    ) {
        val products = launchResultCache.getActualProducts() ?: run {
            handlePurchaseError(QonversionError(QonversionErrorCode.LaunchError), callback)
            return
        }

        val oldProduct: QProduct? = purchaseOptions.options?.oldProduct
            ?: products[purchaseOptions.oldProductId]
        val purchaseOptionsEnriched = purchaseOptions.enrich(oldProduct)
        processPurchase(context, purchaseOptionsEnriched, callback)
    }

    private fun processPurchase(
        context: Activity,
        purchaseOptions: PurchaseOptionsInternalEnriched,
        callback: QonversionPurchaseCallback
    ) {
        if (purchaseOptions.product.storeId == null) {
            handlePurchaseError(QonversionError(QonversionErrorCode.ProductNotFound), callback)
            return
        }

        val purchasingCallback = purchasingCallbacks[purchaseOptions.product.storeId]

        if (purchasingCallback != null) {
            logger.release(
                "purchaseProduct() -> Purchase of the product " +
                        "${purchaseOptions.product.qonversionId} is already in progress. This call will be ignored"
            )
            return
        }

        // Save result callback directly
        purchasingCallbacks[purchaseOptions.product.storeId] = callback

        updatePurchaseOptions(purchaseOptions.options, purchaseOptions.product.storeId)

        billingService.purchase(context, purchaseOptions)
    }

    private fun updatePurchaseOptions(options: QPurchaseOptions?, storeProductId: String?) {
        storeProductId?.let { productId ->
            options?.let {
                processingPurchaseOptions[productId] = it
            } ?: run {
                processingPurchaseOptions.remove(productId)
            }

            purchasesCache.saveProcessingPurchasesOptions(processingPurchaseOptions)
        }
    }

    private fun removePurchaseOptions(productId: String?) {
        updatePurchaseOptions(null, productId)
    }

    private fun handlePurchaseError(
        error: QonversionError,
        callback: QonversionPurchaseCallback
    ) {
        val result = QPurchaseResult.error(error)
        callback.onResult(result)
    }

    fun checkEntitlements(callback: QonversionEntitlementsCallback) {
        entitlementCallbacks.add(callback)

        handlePendingRequests()
    }

    fun restore(requestTrigger: RequestTrigger, callback: QonversionEntitlementsCallback? = null) {
        callback?.let { restoreCallbacks.add(it) }

        if (isRestoreInProgress) {
            return
        }
        isRestoreInProgress = true

        billingService.queryPurchases(
            onFailed = { executeRestoreBlocksOnError(it.toQonversionError()) }
        ) { purchases ->
            if (purchases.isEmpty()) {
                val currentEntitlements = launchResultCache.getActualPermissions()?.toEntitlementsMap() ?: emptyMap()
                executeRestoreBlocksOnSuccess(currentEntitlements)
                return@queryPurchases
            }

            billingService.consumePurchases(purchases)

            val purchaseRecords = purchases.map { PurchaseRecord(it) }
            repository.restore(
                installDate,
                purchaseRecords,
                requestTrigger,
                object : QonversionLaunchCallback {
                    override fun onSuccess(launchResult: QLaunchResult) {
                        updateLaunchResult(launchResult)
                        executeRestoreBlocksOnSuccess(launchResult.permissions.toEntitlementsMap())
                    }

                    override fun onError(error: QonversionError) {
                        if (shouldCalculatePermissionsLocally(error)) {
                            calculateRestorePermissionsLocally(purchases, error)
                        } else {
                            executeRestoreBlocksOnError(error)
                        }
                    }
                }
            )
        }
    }

    fun syncPurchases() {
        restore(RequestTrigger.SyncPurchases)
    }

    override fun onPurchasesCompleted(purchases: List<Purchase>) {
        handlePurchases(purchases, RequestTrigger.Purchase)
    }

    override fun onPurchasesCanceled() {
        // Handle user cancellation for all pending purchases
        val callbacks = purchasingCallbacks.toMap()

        purchasingCallbacks.clear()

        // Call all result callbacks with cancellation
        callbacks.values.forEach {
            // Sometimes the callback might be dead at the moment of invocation
            @Suppress("UNNECESSARY_SAFE_CALL")
            it?.onResult(QPurchaseResult.userCanceled())
        }
    }

    override fun onPurchasesFailed(error: BillingError, purchases: List<Purchase>) {
        if (purchases.isNotEmpty()) {
            purchases.forEach { purchase ->
                val purchaseCallback = purchasingCallbacks[purchase.productId]
                purchasingCallbacks.remove(purchase.productId)

                val qonversionError = error.toQonversionError()
                purchaseCallback?.onResult(QPurchaseResult.error(qonversionError))
            }
        } else {
            val callbacks = purchasingCallbacks.toMap()
            purchasingCallbacks.clear()

            val qonversionError = error.toQonversionError()

            callbacks.values.forEach {
                @Suppress("UNNECESSARY_SAFE_CALL")
                it?.onResult(QPurchaseResult.error(qonversionError))
            }
        }
    }

    // Private functions

    private fun calculateRestorePermissionsLocally(
        purchases: List<Purchase>,
        restoreError: QonversionError
    ) {
        val products = launchResultCache.getActualProducts() ?: run {
            failLocallyGrantingRestorePermissionsWithError(
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        val productPermissions = launchResultCache.getProductPermissions() ?: run {
            failLocallyGrantingRestorePermissionsWithError(restoreError)
            return
        }

        val permissions = grantPermissionsAfterFailedRestore(
            purchases,
            products.values,
            productPermissions
        )

        executeRestoreBlocksOnSuccess(permissions.toEntitlementsMap())
    }

    private fun calculatePurchasePermissionsLocally(
        purchase: Purchase,
        callback: QonversionPurchaseCallback?,
        purchaseError: QonversionError
    ) {
        val products = launchResultCache.getActualProducts() ?: run {
            failLocallyGrantingPurchasePermissionsWithError(
                callback,
                launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            )
            return
        }

        val productPermissions = launchResultCache.getProductPermissions() ?: run {
            failLocallyGrantingPurchasePermissionsWithError(callback, purchaseError)
            return
        }

        val purchasedProduct = products.values.find { product ->
            product.storeId == purchase.productId
        } ?: run {
            failLocallyGrantingPurchasePermissionsWithError(callback, purchaseError)
            return
        }

        val permissions = grantPermissionsAfterFailedPurchaseTracking(
            purchase,
            purchasedProduct,
            productPermissions
        )

        val entitlements = permissions.toEntitlementsMap()
        callback?.onResult(QPurchaseResult.successFromFallback(entitlements, purchase))
    }

    private fun failLocallyGrantingPurchasePermissionsWithError(
        callback: QonversionPurchaseCallback?,
        error: QonversionError
    ) {
        launchResultCache.clearPermissionsCache()
        callback?.onResult(QPurchaseResult.error(error))
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
        val newPermissions = productPermissions[purchasedProduct.qonversionId]?.mapNotNull {
            createPermission(it, purchase.purchaseTime, purchasedProduct)
        } ?: emptyList()

        return mergeManuallyCreatedPermissions(newPermissions)
    }

    private fun grantPermissionsAfterFailedRestore(
        purchases: List<Purchase>,
        products: Collection<QProduct>,
        productPermissions: Map<String, List<String>>
    ): Map<String, QPermission> {
        val newPermissions = purchases
            .asSequence()
            .flatMap { purchase ->
                products
                    .filter { it.storeId == purchase.productId }
                    .flatMap {
                        val permissionIds = productPermissions[it.qonversionId] ?: emptyList()
                        permissionIds.map { permissionId ->
                            createPermission(
                                permissionId,
                                purchase.purchaseTime,
                                it
                            )
                        }
                    }
            }
            .filterNotNull()
            .groupBy { it.permissionId } // handling the case when the same permission is granted for several products
            .map { it.value.first() }
            .toList()

        return mergeManuallyCreatedPermissions(newPermissions)
    }

    private fun createPermission(
        id: String,
        purchaseTime: Long,
        purchasedProduct: QProduct
    ): QPermission? {
        val purchaseDurationInDays = if (purchasedProduct.type === QProductType.InApp) {
            null
        } else {
            min(
                internalConfig.cacheConfig.entitlementsCacheLifetime.days,
                QEntitlementsCacheLifetime.Year.days
            )
        }

        val expirationDate = purchaseDurationInDays?.let { Date(purchaseTime + it.daysToMs) }
        return if (expirationDate == null || Date() < expirationDate) {
            return QPermission(
                id,
                purchasedProduct.qonversionId,
                QProductRenewState.Unknown,
                Date(purchaseTime),
                expirationDate,
                QEntitlementSource.PlayStore,
                1,
                0,
                null,
                null,
                null,
                null,
                QEntitlementGrantType.Purchase,
                null
            )
        } else null
    }

    private fun mergeManuallyCreatedPermissions(
        newPermissions: List<QPermission>
    ): Map<String, QPermission> {
        val existingPermissions = launchResultCache.getActualPermissions() ?: emptyMap()
        val resultPermissions = existingPermissions.toMutableMap()

        newPermissions.forEach { newPermission ->
            val id = newPermission.permissionId
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
        callback: QonversionLaunchCallback,
        requestTrigger: RequestTrigger,
    ) {
        fun processInitDefault() {
            val initRequestData =
                InitRequestData(installDate, advertisingId, null, callback, requestTrigger)
            processInit(initRequestData)
        }

        billingService.queryPurchases(
            onFailed = { processInitDefault() }
        ) { purchases ->
            if (purchases.isEmpty()) {
                processInitDefault()
                return@queryPurchases
            }

            val completedPurchases =
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

            processingPurchases = completedPurchases

            val purchasesInfo = converter.convertPurchases(completedPurchases, processingPurchaseOptions)

            val handledPurchasesCallback =
                getWrappedPurchasesCallback(completedPurchases, callback)

            val initRequestData = InitRequestData(
                installDate,
                advertisingId,
                purchasesInfo,
                handledPurchasesCallback,
                requestTrigger
            )
            processInit(initRequestData)
        }
    }

    private fun getWrappedPurchasesCallback(
        trackingPurchases: List<Purchase>,
        outerCallback: QonversionLaunchCallback
    ): QonversionLaunchCallback {
        return object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) {
                handledPurchasesCache.saveHandledPurchases(trackingPurchases)
                trackingPurchases.forEach {
                    removePurchaseOptions(it.productId)
                }
                outerCallback.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                outerCallback.onError(error)
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

                if (processingPurchases.isNotEmpty()) {
                    handledPurchasesCache.saveHandledPurchases(processingPurchases)

                    billingService.consumePurchases(processingPurchases.toList())
                    processingPurchases = emptyList()
                }

                handleCachedPurchases()

                callback?.onSuccess(launchResult)
            }

            override fun onError(error: QonversionError) {
                launchError = error

                handlePendingRequests(error)

                loadStoreProductsIfPossible()

                callback?.onError(error)
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

            val userId = userInfoService.obtainUserId()
            internalConfig.uid = userId
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
        launch(RequestTrigger.Logout)
    }

    private fun updateLaunchResult(launchResult: QLaunchResult) {
        launchResultCache.save(launchResult)
    }

    private fun loadStoreProductsIfPossible() {
        val products = launchResultCache.getActualProducts() ?: run {
            val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            executeProductsBlocks(error)
            return
        }

        billingService.enrichStoreDataAsync(
            products.values.toList(),
            { error -> executeProductsBlocks(error.toQonversionError()) }
        ) {
            executeProductsBlocks()
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
            repository.purchase(installDate, purchase, null, RequestTrigger.Init, object :
                QonversionLaunchCallback {
                override fun onSuccess(launchResult: QLaunchResult) {
                    updateLaunchResult(launchResult)
                    purchasesCache.clearPurchase(purchase)
                }

                override fun onError(error: QonversionError) {}
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
            fireProductsFailure(callbacks, it)
            return
        }

        val products = launchResultCache.getActualProducts() ?: run {
            val error = launchError ?: QonversionError(QonversionErrorCode.LaunchError)
            fireProductsFailure(callbacks, error)
            return
        }

        val productsList = products.values.toList()
        billingService.enrichStoreData(productsList)
        callbacks.forEach { callback ->
            // Sometimes the callback might be dead at the moment of invocation
            @Suppress("UNNECESSARY_SAFE_CALL")
            callback?.onSuccess(productsList.associateBy { it.qonversionId })
        }
    }

    @Synchronized
    private fun executeEntitlementsBlock(actualError: QonversionError? = null) {
        if (entitlementCallbacks.isEmpty()) {
            return
        }

        val callbacks = entitlementCallbacks.toList()
        entitlementCallbacks.clear()

        preparePermissionsResult(
            { permissions ->
                callbacks.forEach {
                    // Sometimes the callback might be dead at the moment of invocation
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    it?.onSuccess(permissions.toEntitlementsMap())
                }
            },
            { error ->
                callbacks.forEach {
                    // Sometimes the callback might be dead at the moment of invocation
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    it?.onError(error)
                }
            },
            actualError)
    }

    private fun executeRestoreBlocksOnSuccess(entitlements: Map<String, QEntitlement>) {
        val callbacks = restoreCallbacks.toList()
        restoreCallbacks.clear()

        isRestoreInProgress = false

        callbacks.forEach { callback ->
            // Sometimes the callback might be dead at the moment of invocation
            @Suppress("UNNECESSARY_SAFE_CALL")
            callback?.onSuccess(entitlements)
        }

        handlePendingRequests()
    }

    private fun executeRestoreBlocksOnError(error: QonversionError) {
        val callbacks = restoreCallbacks.toList()
        restoreCallbacks.clear()

        isRestoreInProgress = false

        callbacks.forEach { callback ->
            // Sometimes the callback might be dead at the moment of invocation
            @Suppress("UNNECESSARY_SAFE_CALL")
            callback?.onError(error)
        }

        handlePendingRequests(error)
    }

    private fun retryLaunch(
        onSuccess: (QLaunchResult) -> Unit,
        onError: (QonversionError) -> Unit,
        requestTrigger: RequestTrigger,
    ) {
        launch(requestTrigger, object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) = onSuccess(launchResult)
            override fun onError(error: QonversionError) = onError(error)
        })
    }

    private fun fireProductsFailure(
        callbacks: List<QonversionProductsCallback>,
        error: QonversionError
    ) {
        callbacks.forEach {
            // Sometimes the callback might be dead at the moment of invocation
            @Suppress("UNNECESSARY_SAFE_CALL")
            it?.onError(error)
        }
    }

    private fun preparePermissionsResult(
        onSuccess: (permissions: Map<String, QPermission>) -> Unit,
        onError: (QonversionError) -> Unit,
        error: QonversionError?
    ) {
        fun actualizePermissions() {
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
                },
                requestTrigger = RequestTrigger.ActualizePermissions
            )
        }

        val permissions = launchResultCache.getActualPermissions() ?: emptyMap()

        val nowMs = System.currentTimeMillis()
        val permissionsAreActual = permissions.none {
            val expirationTs = it.value.expirationDate?.time ?: Long.MAX_VALUE
            it.value.isActive() && expirationTs < nowMs
        }

        if ((error == null || error.shouldFireFallback) && permissionsAreActual) {
            onSuccess(permissions)
        } else if (error != null) {
            onError(error)
        } else {
            actualizePermissions()
        }
    }

    private fun handlePendingPurchases() {
        if (!isLaunchingFinished) return

        billingService.queryPurchases(onFailed = { /* do nothing */ }) { purchases ->
            handlePurchases(purchases, RequestTrigger.Background)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, requestTrigger: RequestTrigger) {
        billingService.consumePurchases(purchases)

        purchases.forEach { purchase ->
            val purchaseCallback = purchasingCallbacks[purchase.productId]
            purchasingCallbacks.remove(purchase.productId)

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    purchaseCallback?.onResult(QPurchaseResult.pending())
                    return@forEach
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    val error = QonversionError(QonversionErrorCode.PurchaseUnspecified)
                    purchaseCallback?.onResult(QPurchaseResult.error(error))
                    return@forEach
                }
            }

            if (!handledPurchasesCache.shouldHandlePurchase(purchase)) return@forEach

            val product: QProduct? = launchResultCache.getActualProducts()?.values?.find {
                it.storeId == purchase.productId
            }
            val currentPurchaseOptions = processingPurchaseOptions[purchase.productId]
            val purchaseInfo = converter.convertPurchase(purchase, currentPurchaseOptions)
            repository.purchase(
                installDate,
                purchaseInfo,
                product?.qonversionId,
                requestTrigger,
                object : QonversionLaunchCallback {
                    override fun onSuccess(launchResult: QLaunchResult) {
                        updateLaunchResult(launchResult)

                        val entitlements = launchResult.permissions.toEntitlementsMap()

                        removePurchaseOptions(product?.storeId)

                        purchaseCallback?.onResult(QPurchaseResult.success(entitlements, purchase))

                        // If no callback, notify entitlements update listener
                        if (purchaseCallback == null) {
                            internalConfig.entitlementsUpdateListener?.onEntitlementsUpdated(entitlements)
                        }

                        handledPurchasesCache.saveHandledPurchase(purchase)
                    }

                    override fun onError(error: QonversionError) {
                        storeFailedPurchaseIfNecessary(purchase, purchaseInfo, product)

                        removePurchaseOptions(product?.storeId)

                        if (shouldCalculatePermissionsLocally(error)) {
                            calculatePurchasePermissionsLocally(
                                purchase,
                                purchaseCallback,
                                error
                            )
                        } else {
                            purchaseCallback?.onResult(QPurchaseResult.error(error))
                        }
                    }
                })
        }
    }

    private fun storeFailedPurchaseIfNecessary(
        purchase: Purchase,
        purchaseInfo: com.qonversion.android.sdk.internal.dto.purchase.Purchase,
        product: QProduct?
    ) {
        fun storePurchase() {
            purchasesCache.savePurchase(purchaseInfo)
        }

        if (product?.storeDetails?.isInApp == true) {
            storePurchase()
            return
        }

        purchase.productId?.let {
            billingService.getStoreProductType(
                it,
                { storePurchase() }, // saving on error in order not to lose purchase
                { type -> if (type === QStoreProductType.InApp) storePurchase() }
            )
        } ?: storePurchase()
    }

    private fun shouldCalculatePermissionsLocally(error: QonversionError): Boolean {
        return !internalConfig.isAnalyticsMode && (
                error.code == QonversionErrorCode.NetworkConnectionFailed ||
                        error.httpCode?.isInternalServerError() == true
                )
    }

    private fun addIdentityCallback(identityId: String, callback: QonversionUserCallback?) {
        if (callback == null) {
            return
        }

        val callbacks = pendingIdentityCallbacks[identityId] ?: mutableListOf()
        callbacks.add(callback)
        pendingIdentityCallbacks[identityId] = callbacks
    }

    private fun fireIdentitySuccess(identityId: String) {
        val callbacks = pendingIdentityCallbacks[identityId] ?: return
        pendingIdentityCallbacks[identityId] = mutableListOf()

        getUserInfo(object : QonversionUserCallback {
            override fun onSuccess(user: QUser) {
                callbacks.forEach { it.onSuccess(user) }
            }

            override fun onError(error: QonversionError) {
                callbacks.forEach { it.onError(error) }
            }
        })
    }

    private fun fireIdentityError(identityId: String, error: QonversionError) {
        val callbacks = pendingIdentityCallbacks[identityId] ?: return
        pendingIdentityCallbacks[identityId] = mutableListOf()
        callbacks.forEach { it.onError(error) }
    }
}
