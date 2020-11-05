package com.qonversion.android.sdk

import android.app.Activity
import android.app.Application
import android.os.Handler
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.billing.Billing
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.logger.StubLogger
import com.qonversion.android.sdk.storage.TokenStorage
import com.qonversion.android.sdk.storage.UserPropertiesStorage
import com.qonversion.android.sdk.validator.TokenValidator

object Qonversion : LifecycleDelegate{

    private const val SDK_VERSION = "1.1.0"

    private lateinit var repository: QonversionRepository
    private lateinit var userPropertiesManager: QUserPropertiesManager
    private lateinit var attributionManager: QAttributionManager
    private lateinit var productCenterManager: QProductCenterManager

    init {
        val lifecycleHandler = AppLifecycleHandler(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleHandler)
    }

    override fun onAppBackgrounded() {
        userPropertiesManager.forceSendProperties()
    }

    override fun onAppForegrounded() {
        productCenterManager.onAppForegrounded()
    }

    @JvmOverloads
    @JvmStatic
    fun launch(
        context: Application,
        key: String,
        observeMode: Boolean,
        callback: QonversionInitCallback? = null
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
        productCenterManager = QProductCenterManager(context, observeMode, repository, logger)
        productCenterManager.launch(context, callback)
    }

    @JvmStatic
    fun purchaseProduct(id: String, activity: Activity, callback: QonversionPermissionsCallback) {
        productCenterManager.purchaseProduct(id, activity, callback)
    }

    @JvmStatic
    fun products(
        context: Application,
        callback: QonversionProductsCallback
    ) {
        productCenterManager.loadProducts(context, callback)
    }

    @JvmStatic
    fun permissions(
        context: Application,
        callback: QonversionPermissionsCallback
    ) {
        productCenterManager.checkPermissions(context, callback)
    }

    @JvmStatic
    fun restore(callback: QonversionPermissionsCallback) {
        productCenterManager.restore(callback)
    }

    @JvmStatic
    fun syncPurchases() {
        productCenterManager.syncPurchases()
    }

    @JvmStatic
    fun attribution(
        conversionInfo: Map<String, Any>,
        from: AttributionSource,
        conversionUid: String
    ) {
        repository.attribution(conversionInfo, from.id, conversionUid)
    }

    @JvmStatic
    fun setProperty(key: QUserProperties, value: String) {
        userPropertiesManager.setProperty(key, value)
    }

    @JvmStatic
    fun setUserProperty(key: String, value: String) {
        userPropertiesManager.setUserProperty(key, value)
    }

    @JvmStatic
    fun setUserID(value: String) {
        userPropertiesManager.setUserID(value)
    }
}


