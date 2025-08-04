package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.api.RequestTrigger
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseOptionsInternal
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.ExceptionManager
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import com.qonversion.android.sdk.listeners.QonversionUserPropertiesCallback

internal class QonversionInternal(
    internalConfig: InternalConfig,
    application: Application
) : Qonversion, LifecycleDelegate, AppStateProvider {

    private var userPropertiesManager: QUserPropertiesManager
    private var attributionManager: QAttributionManager
    private var productCenterManager: QProductCenterManager
    private var logger = ConsoleLogger()
    private val handler = Handler(Looper.getMainLooper())
    private var sharedPreferencesCache: SharedPreferencesCache
    private var exceptionManager: ExceptionManager
    private var remoteConfigManager: QRemoteConfigManager
    private var fallbackService: QFallbacksService

    override var appState = AppState.Background

    init {
        QDependencyInjector.buildAppComponent(application, internalConfig, this)

        exceptionManager = QDependencyInjector.appComponent.exceptionManager().also {
            it.initialize(application)
        }

        val repository = QDependencyInjector.appComponent.repository()
        val purchasesCache = QDependencyInjector.appComponent.purchasesCache()
        val handledPurchasesCache = QDependencyInjector.appComponent.handledPurchasesCache()
        val launchResultCacheWrapper = QDependencyInjector.appComponent.launchResultCacheWrapper()
        val userInfoService = QDependencyInjector.appComponent.userInfoService()
        val identityManager = QDependencyInjector.appComponent.identityManager()
        sharedPreferencesCache = QDependencyInjector.appComponent.sharedPreferencesCache()

        val userId = userInfoService.obtainUserId()

        internalConfig.uid = userId

        fallbackService = QDependencyInjector.appComponent.fallbacksService()

        userPropertiesManager = QDependencyInjector.appComponent.userPropertiesManager()

        val localRemoteConfigManager = QDependencyInjector.appComponent.remoteConfigManager()

        attributionManager = QAttributionManager(repository, this)

        val factory = QonversionFactory(application, logger)

        productCenterManager = factory.createProductCenterManager(
            repository,
            purchasesCache,
            handledPurchasesCache,
            launchResultCacheWrapper,
            userInfoService,
            identityManager,
            internalConfig,
            this,
            localRemoteConfigManager
        ).also {
            localRemoteConfigManager.userStateProvider = it
        }

        remoteConfigManager = localRemoteConfigManager

        userPropertiesManager.productCenterManager = productCenterManager
        if (internalConfig.primaryConfig.sendFbAttribution) {
            userPropertiesManager.sendFacebookAttribution()
        }

        remoteConfigManager.userPropertiesManager = userPropertiesManager

        val lifecycleHandler = AppLifecycleHandler(this)
        postToMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler) }

        productCenterManager.launch(RequestTrigger.Init)
    }

    override fun onAppBackground() {
        appState = AppState.Background

        userPropertiesManager.onAppBackground()
    }

    override fun onAppForeground() {
        appState = AppState.Foreground

        userPropertiesManager.onAppForeground()
        productCenterManager.onAppForeground()
        attributionManager.onAppForeground()
    }

    @Deprecated("Due to the Google Play Billing Library 8 limitations, this method doesn't restore historical purchases anymore.")
    override fun syncHistoricalData() {
        val isHistoricalDataSynced: Boolean =
            sharedPreferencesCache.getBool(Constants.IS_HISTORICAL_DATA_SYNCED)
        if (isHistoricalDataSynced) {
            return
        }

        productCenterManager.restore(
            RequestTrigger.SyncHistoricalData,
            callback = object : QonversionEntitlementsCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                    sharedPreferencesCache.putBool(Constants.IS_HISTORICAL_DATA_SYNCED, true)
                }

                override fun onError(error: QonversionError) {
                    logger.error("Historical data sync failed.")
                }
            }
        )
    }

    override fun purchase(
        context: Activity,
        product: QProduct,
        options: QPurchaseOptions,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager.purchaseProduct(
            context,
            PurchaseOptionsInternal(product, options),
            mainPurchaseCallback(callback)
        )
    }

    override fun purchase(
        context: Activity,
        product: QProduct,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager.purchaseProduct(
            context,
            PurchaseOptionsInternal(product),
            mainPurchaseCallback(callback)
        )
    }

    override fun updatePurchase(
        context: Activity,
        product: QProduct,
        options: QPurchaseOptions,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager.purchaseProduct(
            context,
            PurchaseOptionsInternal(product, options),
            mainPurchaseCallback(callback)
        )
    }

    override fun products(callback: QonversionProductsCallback) {
        productCenterManager.loadProducts(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) =
                postToMainThread { callback.onSuccess(products) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        })
    }

    override fun offerings(callback: QonversionOfferingsCallback) {
        productCenterManager.offerings(object : QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) =
                postToMainThread { callback.onSuccess(offerings) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        })
    }

    override fun remoteConfig(callback: QonversionRemoteConfigCallback) {
        loadRemoteConfig(null, callback)
    }

    override fun remoteConfig(contextKey: String, callback: QonversionRemoteConfigCallback) {
        loadRemoteConfig(contextKey, callback)
    }

    private fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback) {
        remoteConfigManager.loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                postToMainThread { callback.onSuccess(remoteConfig) }
            }

            override fun onError(error: QonversionError) {
                postToMainThread { callback.onError(error) }
            }
        })
    }

    override fun remoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) {
        remoteConfigManager.loadRemoteConfigList(
            contextKeys,
            includeEmptyContextKey,
            object : QonversionRemoteConfigListCallback {
                override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                    postToMainThread { callback.onSuccess(remoteConfigList) }
                }

                override fun onError(error: QonversionError) {
                    postToMainThread { callback.onError(error) }
                }
            })
    }

    override fun remoteConfigList(callback: QonversionRemoteConfigListCallback) {
        remoteConfigManager.loadRemoteConfigList(object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                postToMainThread { callback.onSuccess(remoteConfigList) }
            }

            override fun onError(error: QonversionError) {
                postToMainThread { callback.onError(error) }
            }
        })
    }

    override fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        remoteConfigManager.attachUserToExperiment(experimentId, groupId, callback)
    }

    override fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        remoteConfigManager.detachUserFromExperiment(experimentId, callback)
    }

    override fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        remoteConfigManager.attachUserToRemoteConfiguration(remoteConfigurationId, callback)
    }

    override fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        remoteConfigManager.detachUserFromRemoteConfiguration(remoteConfigurationId, callback)
    }

    override fun checkTrialIntroEligibility(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    ) {
        productCenterManager.checkTrialIntroEligibilityForProductIds(
            productIds,
            object : QonversionEligibilityCallback {
                override fun onSuccess(eligibilities: Map<String, QEligibility>) =
                    callback.onSuccess(eligibilities)

                override fun onError(error: QonversionError) =
                    callback.onError(error)
            })
    }

    override fun checkEntitlements(callback: QonversionEntitlementsCallback) {
        productCenterManager.checkEntitlements(mainEntitlementsCallback(callback))
    }

    override fun restore(callback: QonversionEntitlementsCallback) {
        productCenterManager.restore(RequestTrigger.Restore, mainEntitlementsCallback(callback))
    }

    override fun syncPurchases() {
        productCenterManager.syncPurchases()
    }

    override fun identify(userId: String) {
        productCenterManager.identify(userId)
    }

    override fun identify(userId: String, callback: QonversionUserCallback) {
        productCenterManager.identify(userId, callback)
    }

    override fun logout() {
        productCenterManager.logout()
    }

    override fun userInfo(callback: QonversionUserCallback) {
        productCenterManager.getUserInfo(mainUserCallback(callback))
    }

    override fun attribution(data: Map<String, Any>, provider: QAttributionProvider) {
        attributionManager.attribution(data, provider)
    }

    override fun setUserProperty(key: QUserPropertyKey, value: String) {
        userPropertiesManager.setUserProperty(key, value)
    }

    override fun setCustomUserProperty(key: String, value: String) {
        userPropertiesManager.setCustomUserProperty(key, value)
    }

    override fun userProperties(callback: QonversionUserPropertiesCallback) {
        userPropertiesManager.userProperties(callback)
    }

    override fun isFallbackFileAccessible(): Boolean {
        val fallbackObject = fallbackService.obtainFallbackData()

        return fallbackObject != null
    }

    override fun setEntitlementsUpdateListener(entitlementsUpdateListener: QEntitlementsUpdateListener) {
        productCenterManager.setEntitlementsUpdateListener(entitlementsUpdateListener)
    }

    // Private functions
    private fun mainEntitlementsCallback(callback: QonversionEntitlementsCallback): QonversionEntitlementsCallback =
        object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                postToMainThread { callback.onSuccess(entitlements) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        }

    private fun mainPurchaseCallback(callback: QonversionEntitlementsCallback): QonversionPurchaseCallback {
        val purchaseCallback = if (callback is QonversionPurchaseCallback) {
            callback
        } else {
            object : QonversionPurchaseCallback {
                override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                    callback.onSuccess(entitlements)
                }

                override fun onError(error: QonversionError) {
                    callback.onError(error)
                }
            }
        }

        return object : QonversionPurchaseCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>, purchase: Purchase) {
                postToMainThread { purchaseCallback.onSuccess(entitlements, purchase) }
            }

            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                postToMainThread { purchaseCallback.onSuccess(entitlements) }

            override fun onError(error: QonversionError) =
                postToMainThread { purchaseCallback.onError(error) }
        }
    }

    private fun mainUserCallback(callback: QonversionUserCallback): QonversionUserCallback =
        object : QonversionUserCallback {
            override fun onSuccess(user: QUser) =
                postToMainThread { callback.onSuccess(user) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        }

    private fun postToMainThread(runnable: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable()
        } else {
            handler.post(runnable)
        }
    }
}
