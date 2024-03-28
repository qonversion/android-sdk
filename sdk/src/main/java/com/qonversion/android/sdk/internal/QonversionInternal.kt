package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.automations.internal.QAutomationsManager
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.QPurchaseModel
import com.qonversion.android.sdk.dto.QPurchaseUpdateModel
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.purchase.PurchaseModelInternal
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.ExceptionManager
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import com.qonversion.android.sdk.listeners.QonversionUserPropertiesCallback

internal class QonversionInternal(
    internalConfig: InternalConfig,
    application: Application
) : Qonversion, LifecycleDelegate, AppStateProvider {

    private var userPropertiesManager: QUserPropertiesManager? = null
    private var attributionManager: QAttributionManager? = null
    private var productCenterManager: QProductCenterManager? = null
    private var automationsManager: QAutomationsManager? = null
    private var logger = ConsoleLogger()
    private val handler = Handler(Looper.getMainLooper())
    private var sharedPreferencesCache: SharedPreferencesCache? = null
    private var exceptionManager: ExceptionManager? = null
    private var remoteConfigManager: QRemoteConfigManager? = null

    override var appState = AppState.Background

    init {
        val lifecycleHandler = AppLifecycleHandler(this)
        postToMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler) }

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

        val userID = userInfoService.obtainUserID()

        internalConfig.uid = userID

        automationsManager = QDependencyInjector.appComponent.automationsManager()

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

        userPropertiesManager?.productCenterManager = productCenterManager
        userPropertiesManager?.sendFacebookAttribution()

        launch()
    }

    override fun onAppBackground() {
        appState = AppState.Background

        userPropertiesManager?.onAppBackground()
    }

    override fun onAppForeground() {
        appState = AppState.Foreground

        userPropertiesManager?.onAppForeground()
        productCenterManager?.onAppForeground()
        automationsManager?.onAppForeground()
        attributionManager?.onAppForeground()
    }

    private fun launch() {
        productCenterManager?.launch(object : QonversionLaunchCallback {
            override fun onSuccess(launchResult: QLaunchResult) =
                postToMainThread { automationsManager?.onLaunchProcessed() }

            override fun onError(error: QonversionError, httpCode: Int?) {}
        })
    }

    override fun syncHistoricalData() {
        val isHistoricalDataSynced: Boolean =
            sharedPreferencesCache?.getBool(Constants.IS_HISTORICAL_DATA_SYNCED) ?: false
        if (isHistoricalDataSynced) {
            return
        }

        Qonversion.shared.restore(callback = object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) {
                sharedPreferencesCache?.putBool(Constants.IS_HISTORICAL_DATA_SYNCED, true)
            }

            override fun onError(error: QonversionError) {
                logger.release("Historical data sync failed.")
            }
        })
    }

    override fun purchase(
        context: Activity,
        purchaseModel: QPurchaseModel,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            PurchaseModelInternal(purchaseModel),
            mainEntitlementsCallback(callback)
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun updatePurchase(
        context: Activity,
        purchaseUpdateModel: QPurchaseUpdateModel,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            PurchaseModelInternal(purchaseUpdateModel),
            mainEntitlementsCallback(callback)
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun products(callback: QonversionProductsCallback) {
        productCenterManager?.loadProducts(object : QonversionProductsCallback {
            override fun onSuccess(products: Map<String, QProduct>) =
                postToMainThread { callback.onSuccess(products) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun offerings(callback: QonversionOfferingsCallback) {
        productCenterManager?.offerings(object : QonversionOfferingsCallback {
            override fun onSuccess(offerings: QOfferings) =
                postToMainThread { callback.onSuccess(offerings) }

            override fun onError(error: QonversionError) =
                postToMainThread { callback.onError(error) }
        }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun remoteConfig(callback: QonversionRemoteConfigCallback) {
        loadRemoteConfig(null, callback)
    }

    override fun remoteConfig(contextKey: String, callback: QonversionRemoteConfigCallback) {
        loadRemoteConfig(contextKey, callback)
    }

    private fun loadRemoteConfig(contextKey: String?, callback: QonversionRemoteConfigCallback) {
        remoteConfigManager?.loadRemoteConfig(contextKey, object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                postToMainThread { callback.onSuccess(remoteConfig) }
            }

            override fun onError(error: QonversionError) {
                postToMainThread { callback.onError(error) }
            }
        }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun remoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    ) {
        remoteConfigManager?.loadRemoteConfigList(
            contextKeys,
            includeEmptyContextKey,
            object : QonversionRemoteConfigListCallback {
                override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                    postToMainThread { callback.onSuccess(remoteConfigList) }
                }

                override fun onError(error: QonversionError) {
                    postToMainThread { callback.onError(error) }
                }
            }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun remoteConfigList(callback: QonversionRemoteConfigListCallback) {
        remoteConfigManager?.loadRemoteConfigList(object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                postToMainThread { callback.onSuccess(remoteConfigList) }
            }

            override fun onError(error: QonversionError) {
                postToMainThread { callback.onError(error) }
            }
        }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun attachUserToExperiment(
        experimentId: String,
        groupId: String,
        callback: QonversionExperimentAttachCallback
    ) {
        remoteConfigManager?.attachUserToExperiment(experimentId, groupId, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback) {
        remoteConfigManager?.detachUserFromExperiment(experimentId, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        remoteConfigManager?.attachUserToRemoteConfiguration(remoteConfigurationId, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    ) {
        remoteConfigManager?.detachUserFromRemoteConfiguration(remoteConfigurationId, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun checkTrialIntroEligibility(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    ) {
        productCenterManager?.checkTrialIntroEligibilityForProductIds(
            productIds,
            object : QonversionEligibilityCallback {
                override fun onSuccess(eligibilities: Map<String, QEligibility>) =
                    callback.onSuccess(eligibilities)

                override fun onError(error: QonversionError) =
                    callback.onError(error)
            }) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun checkEntitlements(callback: QonversionEntitlementsCallback) {
        productCenterManager?.checkEntitlements(mainEntitlementsCallback(callback))
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun restore(callback: QonversionEntitlementsCallback) {
        productCenterManager?.restore(mainEntitlementsCallback(callback))
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun syncPurchases() {
        productCenterManager?.syncPurchases()
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun identify(userID: String) {
        productCenterManager?.identify(userID)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun logout() {
        productCenterManager?.logout()
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun userInfo(callback: QonversionUserCallback) {
        productCenterManager?.getUserInfo(callback)
    }

    override fun attribution(data: Map<String, Any>, provider: QAttributionProvider) {
        attributionManager?.attribution(data, provider)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setUserProperty(key: QUserPropertyKey, value: String) {
        userPropertiesManager?.setUserProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setCustomUserProperty(key: String, value: String) {
        userPropertiesManager?.setCustomUserProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun userProperties(callback: QonversionUserPropertiesCallback) {
        userPropertiesManager?.userProperties(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setEntitlementsUpdateListener(entitlementsUpdateListener: QEntitlementsUpdateListener) {
        productCenterManager?.setEntitlementsUpdateListener(entitlementsUpdateListener)
    }

    // Internal functions
    private fun logLaunchErrorForFunctionName(functionName: String?) {
        logger.release("$functionName function can not be executed. It looks like launch was not called.")
    }

    // Private functions
    private fun mainEntitlementsCallback(callback: QonversionEntitlementsCallback): QonversionEntitlementsCallback =
        object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                postToMainThread { callback.onSuccess(entitlements) }

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
