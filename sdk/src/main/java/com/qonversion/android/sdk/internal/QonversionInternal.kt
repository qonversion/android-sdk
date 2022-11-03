package com.qonversion.android.sdk.internal

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingFlowParams
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.internal.QAutomationsManager
import com.qonversion.android.sdk.dto.QAttributionSource
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QUserProperties
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionUserCallback

internal class QonversionInternal(
    internalConfig: InternalConfig,
    application: Application
) : Qonversion, LifecycleDelegate, AppStateProvider {

    private var userPropertiesManager: QUserPropertiesManager? = null
    private var attributionManager: QAttributionManager? = null
    private var productCenterManager: QProductCenterManager? = null
    private var automationsManager: QAutomationsManager? = null
    private var logger = ConsoleLogger()
    private var isDebugMode = false
    private val handler = Handler(Looper.getMainLooper())

    override var appState = AppState.Background

    init {
        val lifecycleHandler = AppLifecycleHandler(this)
        postToMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler) }

        QDependencyInjector.buildAppComponent(application, internalConfig, this)

        val repository = QDependencyInjector.appComponent.repository()
        val purchasesCache = QDependencyInjector.appComponent.purchasesCache()
        val handledPurchasesCache = QDependencyInjector.appComponent.handledPurchasesCache()
        val launchResultCacheWrapper = QDependencyInjector.appComponent.launchResultCacheWrapper()
        val userInfoService = QDependencyInjector.appComponent.userInfoService()
        val identityManager = QDependencyInjector.appComponent.identityManager()
        val config = QDependencyInjector.appComponent.internalConfig()

        val userID = userInfoService.obtainUserID()

        config.uid = userID

        automationsManager = QDependencyInjector.appComponent.automationsManager()

        userPropertiesManager = QDependencyInjector.appComponent.userPropertiesManager()

        attributionManager = QAttributionManager(repository, this)

        val factory = QonversionFactory(application, logger)

        productCenterManager = factory.createProductCenterManager(
            repository,
            purchasesCache,
            handledPurchasesCache,
            launchResultCacheWrapper,
            userInfoService,
            identityManager,
            config,
            this
        )

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

            override fun onError(error: QonversionError) {}
        })
    }

    override fun purchase(context: Activity, id: String, callback: QonversionEntitlementsCallback) {
        productCenterManager?.purchaseProduct(
            context,
            id,
            null,
            null,
            null,
            mainEntitlementsCallback(callback)
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun purchase(context: Activity, product: QProduct, callback: QonversionEntitlementsCallback) {
        productCenterManager?.purchaseProduct(
            context,
            product,
            null,
            null,
            mainEntitlementsCallback(callback)
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun updatePurchase(
        context: Activity,
        productId: String,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            productId,
            oldProductId,
            prorationMode,
            null,
            mainEntitlementsCallback(callback)
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun updatePurchase(
        context: Activity,
        product: QProduct,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int?,
        callback: QonversionEntitlementsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            product,
            oldProductId,
            prorationMode,
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

    override fun checkTrialIntroEligibilityForProductIds(
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

    override fun attribution(conversionInfo: Map<String, Any>, from: QAttributionSource) {
        attributionManager?.attribution(conversionInfo, from)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setProperty(key: QUserProperties, value: String) {
        userPropertiesManager?.setProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setUserProperty(key: String, value: String) {
        userPropertiesManager?.setUserProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    override fun setDebugMode() {
        isDebugMode = true
    }

    override fun setEntitlementsUpdateListener(entitlementsUpdateListener: EntitlementsUpdateListener) {
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
