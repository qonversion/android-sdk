package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.os.Handler
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.android.billingclient.api.BillingFlowParams
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.StubLogger
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.storage.UserPropertiesStorage
import com.qonversion.android.sdk.validator.TokenValidator

object Qonversion : LifecycleDelegate{

    private const val SDK_VERSION = "2.0.1"

    private lateinit var repository: QonversionRepository
    private lateinit var userPropertiesManager: QUserPropertiesManager
    private lateinit var attributionManager: QAttributionManager
    private var productCenterManager: QProductCenterManager? = null
    private var logger = if (BuildConfig.DEBUG) ConsoleLogger() else StubLogger()

    init {
        val lifecycleHandler = AppLifecycleHandler(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler)
    }

    override fun onAppBackground() {
        userPropertiesManager.forceSendProperties()
    }

    override fun onAppForeground() {
        productCenterManager?.onAppForeground()
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
        if (key.isEmpty()) {
            throw RuntimeException("Qonversion initialization error! Key should not be empty!")
        }

        val logger = if (BuildConfig.DEBUG) {
            ConsoleLogger()
        } else {
            StubLogger()
        }
        val storage = TokenStorage(
            PreferenceManager.getDefaultSharedPreferences(context),
            TokenValidator()
        )
        val propertiesStorage = UserPropertiesStorage()
        val environment = EnvironmentProvider(context)
        val config = QonversionConfig(key, Qonversion.SDK_VERSION, true)
        val repository = QonversionRepository.initialize(
            context,
            storage,
            propertiesStorage,
            logger,
            environment,
            config,
            null
        )

        this.repository = repository
        userPropertiesManager = QUserPropertiesManager(repository, context.contentResolver, Handler(context.mainLooper))
        attributionManager = QAttributionManager()
        val billingCreator = BillingServiceCreator(context, logger)
        productCenterManager = QProductCenterManager(context, observeMode, repository, billingCreator, logger)
        productCenterManager?.launch(context, callback)
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
        productCenterManager?.purchaseProduct(context, id, null, null, callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
    }

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param productId Qonversion product identifier for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmOverloads
    @JvmStatic
    fun updatePurchase(context: Activity, productId: String, oldProductId: String, callback: QonversionPermissionsCallback) {
        productCenterManager?.purchaseProduct(context, productId, oldProductId, null, callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
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
    @JvmOverloads
    @JvmStatic
    fun updatePurchase(context: Activity, productId: String, oldProductId: String, @BillingFlowParams.ProrationMode prorationMode: Int?, callback: QonversionPermissionsCallback) {
        productCenterManager?.purchaseProduct(context, productId, oldProductId, prorationMode, callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
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
        productCenterManager?.loadProducts(callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
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
        productCenterManager?.checkPermissions(callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
    }

    /**
     * Restore user Products
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    @JvmStatic
    fun restore(callback: QonversionPermissionsCallback) {
        productCenterManager?.restore(callback) ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
    }

    /**
     * This method will send all purchases to the Qonversion backend. Call this every time when purchase is handled by you own implementation.
     * @warning This function should only be called if you're using Qonversion SDK in observer mode.
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     */
    @JvmStatic
    fun syncPurchases() {
        productCenterManager?.syncPurchases() ?: logLaunchErrorForFunctionName(object{}.javaClass.enclosingMethod?.name)
    }

    /**
     * Send your attribution data
     * @param conversionInfo map received by the attribution source
     * @param from Attribution source
     * @param conversionUid conversion uid
     */
    @JvmStatic
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource,
        conversionUid: String
    ) {
        repository.attribution(conversionInfo, from.id, conversionUid)
    }

    /**
     * Sets Qonversion reserved user properties, like email or one-signal id
     * @param key defined enum key that will be transformed to string
     * @param value property value
     */
    @JvmStatic
    fun setProperty(key: QUserProperties, value: String) {
        userPropertiesManager.setProperty(key, value)
    }

    /**
     * Sets custom user properties
     * @param key custom user property key
     * @param value property value
     */
    @JvmStatic
    fun setUserProperty(key: String, value: String) {
        userPropertiesManager.setUserProperty(key, value)
    }

    /**
     * Associate a user with their unique ID in your system
     * @param value your database user ID
     */
    @JvmStatic
    fun setUserID(value: String) {
        userPropertiesManager.setUserID(value)
    }

    // Private functions

    private fun logLaunchErrorForFunctionName(functionName: String?) {
        logger.log("$functionName function can not be executed. It looks like launch was not called.")
    }
}


