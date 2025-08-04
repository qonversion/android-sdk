package com.qonversion.android.sdk

import android.app.Activity
import android.util.Log
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.QPurchaseOptions
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigurationAttachCallback
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import com.qonversion.android.sdk.listeners.QonversionUserPropertiesCallback

interface Qonversion {

    companion object {

        private var backingInstance: Qonversion? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion SDK.
         * Please, use the property only after calling [Qonversion.initialize].
         * Otherwise, trying to access the variable will cause an exception.
         *
         * @return Current initialized instance of the Qonversion SDK.
         * @throws UninitializedPropertyAccessException if the instance has not been initialized
         */
        @JvmStatic
        @get:JvmName("getSharedInstance")
        val shared: Qonversion
            get() = backingInstance ?: throw UninitializedPropertyAccessException(
                "Qonversion has not been initialized. You should call " +
                        "the initialize method before accessing the shared instance of Qonversion."
            )

        /**
         * An entry point to use Qonversion SDK. Call to initialize Qonversion SDK with required and extra configs.
         * The function is the best way to set additional configs you need to use Qonversion SDK.
         * You still have an option to set a part of additional configs later via calling separated setters.
         *
         * @param config a config that contains key SDK settings.
         * Call [QonversionConfig.Builder.build] to configure and create a QonversionConfig instance.
         * @return Initialized instance of the Qonversion SDK.
         */
        @JvmStatic
        fun initialize(config: QonversionConfig): Qonversion {
            backingInstance?.let {
                Log.e(
                    "Qonversion", "Qonversion has been initialized already. " +
                            "Multiple instances of Qonversion are not supported now."
                )
                return it
            }

            val internalConfig = InternalConfig(config)

            return QonversionInternal(internalConfig, config.application).also {
                backingInstance = it
            }
        }
    }

    /**
     * Call this function to sync the subscriber data with the first launch
     * when Qonversion is implemented.
     *
     * You don't need to care about single call of this function during the application lifetime.
     * Qonversion will take care about it.
     */
    @Deprecated("Due to the Google Play Billing Library 8 limitations, this method doesn't restore historical purchases anymore.")
    fun syncHistoricalData()

    /**
     * Make a purchase and validate it through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product product for purchase
     * @param options necessary information for purchase
     * @param callback - callback that will be called when response is received
     * @see [Making Purchases](https://documentation.qonversion.io/docs/making-purchases)
     */
    fun purchase(
        context: Activity,
        product: QProduct,
        options: QPurchaseOptions,
        callback: QonversionEntitlementsCallback
    )

    /**
     * Make a purchase and validate it through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product product for purchase
     * @param callback - callback that will be called when response is received
     * @see [Making Purchases](https://documentation.qonversion.io/docs/making-purchases)
     */
    fun purchase(
        context: Activity,
        product: QProduct,
        callback: QonversionEntitlementsCallback
    )

    /**
     * Update (upgrade/downgrade) subscription and validate it through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product product for purchase
     * @param options necessary information for purchase
     * @param callback - callback that will be called when response is received
     * @see [Update policy](https://developer.android.com/google/play/billing/subscriptions#replacement-modes)
     * @see [Making Purchases](https://documentation.qonversion.io/docs/making-purchases)
     */
    fun updatePurchase(
        context: Activity,
        product: QProduct,
        options: QPurchaseOptions,
        callback: QonversionEntitlementsCallback
    )

    /**
     * Return Qonversion Products in association with Google Play Store Products
     * If you get an empty SkuDetails be sure your products are correctly set up in Google Play Store.
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun products(callback: QonversionProductsCallback)

    /**
     * Return Qonversion Offerings Object
     * An offering is a group of products that you can offer to a user on a given paywall based on your business logic.
     * For example, you can offer one set of products on a paywall immediately after onboarding and another set of products with discounts later on if a user has not converted.
     * Offerings allow changing the products offered remotely without releasing app updates.
     * @see [Offerings](https://qonversion.io/docs/offerings)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun offerings(callback: QonversionOfferingsCallback)

    /**
     * Returns default Qonversion remote config object
     * Use this function to get the remote config with specific payload and experiment info.
     * @param callback - callback that will be called when response is received
     */
    fun remoteConfig(callback: QonversionRemoteConfigCallback)

    /**
     * Returns Qonversion remote config object by [contextKey].
     * Use this function to get the remote config with specific payload and experiment info.
     * @param callback - callback that will be called when response is received
     */
    fun remoteConfig(contextKey: String, callback: QonversionRemoteConfigCallback)

    /**
     * Returns Qonversion remote config objects by a list of [contextKeys].
     * Use this function to get the remote configs with specific payload and experiment info.
     * @param includeEmptyContextKey - set to true if you want to include remote config
     *                              with empty context key to the result
     * @param callback - callback that will be called when response is received
     */
    fun remoteConfigList(
        contextKeys: List<String>,
        includeEmptyContextKey: Boolean,
        callback: QonversionRemoteConfigListCallback
    )

    /**
     * Returns Qonversion remote config objects for all existing context key (including empty one).
     * Use this function to get the remote configs with specific payload and experiment info.
     * @param callback - callback that will be called when response is received
     */
    fun remoteConfigList(callback: QonversionRemoteConfigListCallback)

    /**
     * This function should be used for the test purposes only. Do not forget to delete the usage of this function before the release.
     * Use this function to attach the user to the experiment.
     * @param experimentId identifier of the experiment
     * @param groupId identifier of the experiment group
     * @param callback callback that includes information about the result of the action
     */
    fun attachUserToExperiment(experimentId: String, groupId: String, callback: QonversionExperimentAttachCallback)

    /**
     * This function should be used for the test purposes only. Do not forget to delete the usage of this function before the release.
     * Use this function to detach the user to the experiment.
     * @param experimentId identifier of the experiment
     * @param callback callback that includes information about the result of the action
     */
    fun detachUserFromExperiment(experimentId: String, callback: QonversionExperimentAttachCallback)

    /**
     * This function should be used for the test purposes only. Do not forget to delete the usage of this function before the release.
     * Use this function to attach the user to the remote configuration.
     * @param remoteConfigurationId identifier of the remote configuration
     * @param callback callback that includes information about the result of the action
     */
    fun attachUserToRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    )

    /**
     * This function should be used for the test purposes only. Do not forget to delete the usage of this function before the release.
     * Use this function to detach the user from the remote configuration.
     * @param remoteConfigurationId identifier of the remote configuration
     * @param callback callback that includes information about the result of the action
     */
    fun detachUserFromRemoteConfiguration(
        remoteConfigurationId: String,
        callback: QonversionRemoteConfigurationAttachCallback
    )

    /**
     * You can check if a user is eligible for an introductory offer, including a free trial.
     * You can show only a regular price for users who are not eligible for an introductory offer.
     * @param productIds products identifiers that must be checked
     * @param callback - callback that will be called when response is received
     */
    fun checkTrialIntroEligibility(
        productIds: List<String>,
        callback: QonversionEligibilityCallback
    )

    /**
     * Check user entitlements based on product center details
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun checkEntitlements(callback: QonversionEntitlementsCallback)

    /**
     * Restore active user purchases and grant entitlements for them.
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun restore(callback: QonversionEntitlementsCallback)

    /**
     * This method will send all the active purchases to the Qonversion backend.
     * Call this every time when a purchase is handled by your own implementation.
     * @warning This function should only be called if you're using Qonversion SDK in analytics mode.
     * @see [Analytics mode](https://qonversion.io/docs/observer-mode)
     */
    fun syncPurchases()

    /**
     * Call this function to link a user to his unique identifier in your system and share purchase data.
     * @param userId - unique user identifier in your system
     */
    fun identify(userId: String)

    /**
     * Call this function to link a user to his unique identifier in your system and share purchase data.
     * @param userId - unique user identifier in your system
     * @param callback - callback that will be called when response is received
     */
    fun identify(userId: String, callback: QonversionUserCallback)

    /**
     * Call this function to unlink a user from his unique identifier in your system and his purchase data.
     */
    fun logout()

    /**
     * This method returns information about the current Qonversion user.
     * @param callback - callback that will be called when response is received
     */
    fun userInfo(callback: QonversionUserCallback)

    /**
     * Send your attribution data
     * @param data map received by the attribution source
     * @param provider Attribution provider
     */
    fun attribution(data: Map<String, Any>, provider: QAttributionProvider)

    /**
     * Sets Qonversion reserved user properties, like email or user id.
     * Note that using [QUserPropertyKey.Custom] here will do nothing.
     * To set custom user property, use [setCustomUserProperty] method instead.
     * @param key defined enum key that will be transformed to string
     * @param value property value
     */
    fun setUserProperty(key: QUserPropertyKey, value: String)

    /**
     * Sets custom user property
     * @param key custom user property key
     * @param value property value
     */
    fun setCustomUserProperty(key: String, value: String)

    /**
     * This method returns all the properties, set for the current Qonversion user.
     * All set properties are sent to the server with delay, so if you call
     * this function right after setting some property, it may not be included
     * in the result.
     * @param callback - callback that will be called when response is received
     */
    fun userProperties(callback: QonversionUserPropertiesCallback)

    /**
     * Call this function to check if the fallback file is accessible.
     * @return flag that indicates whether Qonversion was able to read data from the fallback file or not.
     */
    fun isFallbackFileAccessible(): Boolean

    /**
     * Provide a listener to be notified about asynchronous user entitlements updates.
     *
     * Make sure you provide this listener for being up-to-date with the user entitlements.
     * Else you can lose some important updates. Also, please, consider that this listener
     * should live for the whole lifetime of the application.
     *
     * You may set entitlements listener both *after* Qonversion SDK initializing
     * with [Qonversion.setEntitlementsUpdateListener] and *while* Qonversion initializing
     * with [Qonversion.initialize]
     *
     * @param entitlementsUpdateListener listener to be called when entitlements update.
     */
    fun setEntitlementsUpdateListener(entitlementsUpdateListener: QEntitlementsUpdateListener)
}
