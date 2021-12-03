package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingFlowParams
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.di.QDependencyInjector
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.QAutomationsManager
import com.qonversion.android.sdk.dto.products.QProduct

object Qonversion : LifecycleDelegate {

    private var userPropertiesManager: QUserPropertiesManager? = null
    private var attributionManager: QAttributionManager? = null
    private var productCenterManager: QProductCenterManager? = null
    private var automationsManager: QAutomationsManager? = null
    private var logger = ConsoleLogger()
    private var isDebugMode = false
    private val handler = Handler(Looper.getMainLooper())
    internal var appState = AppState.Background

    init {
        val lifecycleHandler = AppLifecycleHandler(this)
        postToMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler) }
    }

    override fun onAppBackground() {
        if (!QDependencyInjector.isAppComponentInitialized()) {
            appState = AppState.PendingBackground
            return
        }

        appState = AppState.Background

        userPropertiesManager?.onAppBackground()
    }

    override fun onAppForeground() {
        if (!QDependencyInjector.isAppComponentInitialized()) {
            appState = AppState.PendingForeground
            return
        }

        appState = AppState.Foreground

        userPropertiesManager?.onAppForeground()
        productCenterManager?.onAppForeground()
        automationsManager?.onAppForeground()
        attributionManager?.onAppForeground()
    }

    /**
     * Launches Qonversion SDK with the given project key, you can get one in your account on https://dash.qonversion.io
     * @param context Application object
     * @param key project key to setup the SDK
     * @param observeMode set true if you are using observer mode only
     * @param callback - callback that will be called when response is received
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     * @see [Installing the Android SDK](https://qonversion.io/docs/google)
     */
    @JvmStatic
    @JvmOverloads
    fun launch(
        context: Application,
        key: String,
        observeMode: Boolean,
        callback: QonversionLaunchCallback? = null
    ) {
        QDependencyInjector.buildAppComponent(context, key, isDebugMode)

        if (key.isEmpty()) {
            throw RuntimeException("Qonversion initialization error! Key should not be empty!")
        }

        val repository = QDependencyInjector.appComponent.repository()
        val purchasesCache = QDependencyInjector.appComponent.purchasesCache()
        val handledPurchasesCache = QDependencyInjector.appComponent.handledPurchasesCache()
        val launchResultCacheWrapper = QDependencyInjector.appComponent.launchResultCacheWrapper()
        val userInfoService = QDependencyInjector.appComponent.userInfoService()
        val identityManager = QDependencyInjector.appComponent.identityManager()
        val config = QDependencyInjector.appComponent.qonversionConfig()

        val userID = userInfoService.obtainUserID()

        config.uid = userID

        automationsManager = QDependencyInjector.appComponent.automationsManager()

        userPropertiesManager = QDependencyInjector.appComponent.userPropertiesManager()
        userPropertiesManager?.sendFacebookAttribution()

        attributionManager = QAttributionManager(repository)

        val factory = QonversionFactory(context, logger)

        productCenterManager = factory.createProductCenterManager(
            repository,
            observeMode,
            purchasesCache,
            handledPurchasesCache,
            launchResultCacheWrapper,
            userInfoService,
            identityManager,
            config
        )

        when (appState) {
            AppState.PendingForeground -> onAppForeground()
            AppState.PendingBackground -> onAppBackground()
            else -> {}
        }

        productCenterManager?.launch(callback)
    }

    /**
     * Make a purchase and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param id Qonversion product identifier for purchase
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun purchase(context: Activity, id: String, callback: QonversionPermissionsCallback) {
        productCenterManager?.purchaseProduct(context, id, null, null, null, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Make a purchase and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product Qonversion product for purchase
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun purchase(context: Activity, product: QProduct, callback: QonversionPermissionsCallback) {
        productCenterManager?.purchaseProduct(context, product, null, null, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param productId Qonversion product identifier for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param prorationMode proration mode
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    @JvmOverloads
    fun updatePurchase(
        context: Activity,
        productId: String,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null,
        callback: QonversionPermissionsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            productId,
            oldProductId,
            prorationMode,
            null,
            callback
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product Qonversion product for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param prorationMode proration mode
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    @JvmOverloads
    fun updatePurchase(
        context: Activity,
        product: QProduct,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null,
        callback: QonversionPermissionsCallback
    ) {
        productCenterManager?.purchaseProduct(
            context,
            product,
            oldProductId,
            prorationMode,
            callback
        ) ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Return Qonversion Products in assoсiation with Google Play Store Products
     * If you get an empty SkuDetails be sure your products are correctly setted up in Google Play Store.
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun products(
        callback: QonversionProductsCallback
    ) {
        productCenterManager?.loadProducts(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Return Qonversion Offerings Object
     * An offering is a group of products that you can offer to a user on a given paywall based on your business logic.
     * For example, you can offer one set of products on a paywall immediately after onboarding and another set of products with discounts later on if a user has not converted.
     * Offerings allow changing the products offered remotely without releasing app updates.
     * @see [Offerings](https://qonversion.io/docs/offerings)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun offerings(
        callback: QonversionOfferingsCallback
    ) {
        productCenterManager?.offerings(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Qonversion A/B tests help you grow your app revenue by making it easy to run and analyze paywall and promoted in-app product experiments. It gives you the power to measure your paywalls' performance before you roll them out widely. It is an out-of-the-box solution that does not require any third-party service.
     * @param callback - callback that will be called when response is received
     */
    @JvmStatic
    fun experiments(
        callback: QonversionExperimentsCallback
    ) {
        productCenterManager?.experiments(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * You can check if a user is eligible for an introductory offer, including a free trial.
     * You can show only a regular price for users who are not eligible for an introductory offer.
     * @param productIds products identifiers that must be checked
     * @param callback - callback that will be called when response is received
     */
    @JvmStatic
    fun checkTrialIntroEligibilityForProductIds(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    ) {
        productCenterManager?.checkTrialIntroEligibilityForProductIds(productIds, callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Check user permissions based on product center details
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun checkPermissions(
        callback: QonversionPermissionsCallback
    ) {
        productCenterManager?.checkPermissions(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Restore user Products
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun restore(callback: QonversionPermissionsCallback) {
        productCenterManager?.restore(callback)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * This method will send all purchases to the Qonversion backend. Call this every time when purchase is handled by you own implementation.
     * @warning This function should only be called if you're using Qonversion SDK in observer mode.
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     */
    @JvmStatic
    fun syncPurchases() {
        productCenterManager?.syncPurchases()
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Call this function to link a user to his unique ID in your system and share purchase data.
     * @param userID - unique user ID in your system
     */
    @JvmStatic
    fun identify(userID: String) {
        productCenterManager?.identify(userID)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Call this function to unlink a user from his unique ID in your system and his purchase data.
     */
    @JvmStatic
    fun logout() {
        productCenterManager?.logout()
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Call this function to reset user ID and generate new anonymous user ID.
     * Call this function before Qonversion.launch()
     */
    @Deprecated(
        "This function was used in debug mode only. You can reinstall the app if you need to reset the user ID.",
        level = DeprecationLevel.WARNING
    )
    @JvmStatic
    fun resetUser() {
        logger.debug(object {}.javaClass.enclosingMethod?.name +
                " function was used in debug mode only. You can reinstall the app if you need to reset the user ID.")
    }

    /**
     * Send your attribution data
     * @param conversionInfo map received by the attribution source
     * @param from Attribution source
     */
    @JvmStatic
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource
    ) {
        attributionManager?.attribution(conversionInfo, from)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Sets Qonversion reserved user properties, like email or one-signal id
     * @param key defined enum key that will be transformed to string
     * @param value property value
     */
    @JvmStatic
    fun setProperty(key: QUserProperties, value: String) {
        userPropertiesManager?.setProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Sets custom user properties
     * @param key custom user property key
     * @param value property value
     */
    @JvmStatic
    fun setUserProperty(key: String, value: String) {
        userPropertiesManager?.setUserProperty(key, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Associate a user with their unique ID in your system
     * @param value your database user ID
     */
    @JvmStatic
    @Deprecated(
        "Will be removed in a future major release. Use setProperty instead.",
        replaceWith = ReplaceWith(
            "Qonversion.setProperty(QUserProperties.CustomUserId, value)",
            "com.qonversion.android.sdk.QUserProperties"
        )
    )
    fun setUserID(value: String) {
        userPropertiesManager?.setProperty(QUserProperties.CustomUserId, value)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Set the delegate to handle pending purchases
     * The delegate is called when the deferred transaction status updates
     * For example, to handle purchases using slow credit card or SCA flow purchases
     */
    @JvmStatic
    fun setUpdatedPurchasesListener(listener: UpdatedPurchasesListener) {
        productCenterManager?.setUpdatedPurchasesListener(listener)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * You can set the flag to distinguish sandbox and production users.
     * To see the sandbox users turn on the Viewing test Data toggle on Qonversion Dashboard
     */
    @JvmStatic
    fun setDebugMode() {
        isDebugMode = true
    }

    /**
     * Set push token to Qonversion to enable Qonversion push notifications
     */
    @JvmStatic
    fun setNotificationsToken(token: String) {
        automationsManager?.setPushToken(token)
            ?: logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
    }

    /**
     * Returns true when a push notification was received from Qonversion.
     * Otherwise returns false, so you need to handle a notification yourself
     */
    @JvmStatic
    fun handleNotification(remoteMessage: RemoteMessage) =
        automationsManager?.handlePushIfPossible(remoteMessage) ?: run {
            logLaunchErrorForFunctionName(object {}.javaClass.enclosingMethod?.name)
            return@run false
        }

    // Internal functions
    internal fun logLaunchErrorForFunctionName(functionName: String?) {
        logger.release("$functionName function can not be executed. It looks like launch was not called.")
    }

    // Private functions
    private fun postToMainThread(runnable: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable()
        } else {
            handler.post(runnable)
        }
    }
}
