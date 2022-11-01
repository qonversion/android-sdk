package com.qonversion.android.sdk

import android.app.Activity
import com.android.billingclient.api.BillingFlowParams
import com.qonversion.android.sdk.dto.QAttributionSource
import com.qonversion.android.sdk.dto.QUserProperties
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionLaunchCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionUserCallback

interface Qonversion {

    companion object {

        private var backingInstance: Qonversion? = null

        /**
         * Use this variable to get a current initialized instance of the Qonversion SDK.
         * Please, use the variable only after calling [Qonversion.initialize].
         * Otherwise, trying to access the variable will cause an exception.
         *
         * @return Current initialized instance of the Qonversion SDK.
         * @throws UninitializedPropertyAccessException if the instance has not been initialized
         */
        @JvmStatic
        val sharedInstance: Qonversion
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
            val internalConfig = InternalConfig(config)

            return QonversionInternal(internalConfig, config.application).also {
                backingInstance = it
            }
        }
    }

    /**
     * Launches Qonversion SDK with the given project key, you can get one in your account on https://dash.qonversion.io
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     * @see [Installing the Android SDK](https://qonversion.io/docs/google)
     */
    fun launch() = launch(null)

    /**
     * Launches Qonversion SDK with the given project key, you can get one in your account on https://dash.qonversion.io
     * @param callback - callback that will be called when response is received
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     * @see [Installing the Android SDK](https://qonversion.io/docs/google)
     * // todo overload for java
     */
    fun launch(callback: QonversionLaunchCallback?)

    /**
     * Make a purchase and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param id Qonversion product identifier for purchase
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun purchase(context: Activity, id: String, callback: QonversionEntitlementsCallback)

    /**
     * Make a purchase and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product Qonversion product for purchase
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun purchase(context: Activity, product: QProduct, callback: QonversionEntitlementsCallback)

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param productId Qonversion product identifier for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun updatePurchase(
        context: Activity,
        productId: String,
        oldProductId: String,
        callback: QonversionEntitlementsCallback
    ) = updatePurchase(context, productId, oldProductId, null, callback)

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param productId Qonversion product identifier for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param prorationMode proration mode
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     * // todo override for java
     */
    fun updatePurchase(
        context: Activity,
        productId: String,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null,
        callback: QonversionEntitlementsCallback
    )

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product Qonversion product for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun updatePurchase(
        context: Activity,
        product: QProduct,
        oldProductId: String,
        callback: QonversionEntitlementsCallback
    ) = updatePurchase(context, product, oldProductId, null, callback)

    /**
     * Update (upgrade/downgrade) subscription and validate that through server-to-server using Qonversion's Backend
     * @param context current activity context
     * @param product Qonversion product for purchase
     * @param oldProductId Qonversion product identifier from which the upgrade/downgrade will be initialized
     * @param prorationMode proration mode
     * @param callback - callback that will be called when response is received
     * @see [Proration mode](https://developer.android.com/google/play/billing/subscriptions#proration)
     * @see [Product Center](https://qonversion.io/docs/product-center)
     * // todo override for java
     */
    fun updatePurchase(
        context: Activity,
        product: QProduct,
        oldProductId: String,
        @BillingFlowParams.ProrationMode prorationMode: Int? = null,
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
     * You can check if a user is eligible for an introductory offer, including a free trial.
     * You can show only a regular price for users who are not eligible for an introductory offer.
     * @param productIds products identifiers that must be checked
     * @param callback - callback that will be called when response is received
     */
    fun checkTrialIntroEligibilityForProductIds(
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
     * Restore user Products
     * @param callback - callback that will be called when response is received
     * @see [Product Center](https://qonversion.io/docs/product-center)
     */
    fun restore(callback: QonversionEntitlementsCallback)

    /**
     * This method will send all purchases to the Qonversion backend. Call this every time when purchase is handled by you own implementation.
     * @warning This function should only be called if you're using Qonversion SDK in observer mode.
     * @see [Observer mode](https://qonversion.io/docs/observer-mode)
     */
    fun syncPurchases()

    /**
     * Call this function to link a user to his unique ID in your system and share purchase data.
     * @param userID - unique user ID in your system
     */
    fun identify(userID: String)

    /**
     * Call this function to unlink a user from his unique ID in your system and his purchase data.
     */
    fun logout()

    /**
     * This method returns information about the current Qonversion user.
     * @param callback - callback that will be called when response is received
     */
    fun getUserInfo(callback: QonversionUserCallback)

    /**
     * Send your attribution data
     * @param conversionInfo map received by the attribution source
     * @param from Attribution source
     */
    fun attribution(conversionInfo: Map<String, Any>, from: QAttributionSource)

    /**
     * Sets Qonversion reserved user properties, like email or one-signal id
     * @param key defined enum key that will be transformed to string
     * @param value property value
     */
    fun setProperty(key: QUserProperties, value: String)

    /**
     * Sets custom user properties
     * @param key custom user property key
     * @param value property value
     */
    fun setUserProperty(key: String, value: String)

    /**
     * You can set the flag to distinguish sandbox and production users.
     * To see the sandbox users turn on the Viewing test Data toggle on Qonversion Dashboard
     */
    fun setDebugMode()

    /**
     * Set push token to Qonversion to enable Qonversion push notifications
     */
    fun setNotificationsToken(token: String)

    /**
     * @param messageData RemoteMessage payload data
     * @see [RemoteMessage data](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/RemoteMessage#public-mapstring,-string-getdata)
     * @return true when a push notification was received from Qonversion. Otherwise returns false, so you need to handle a notification yourself
     */
    fun handleNotification(messageData: Map<String, String>): Boolean

    /**
     * Get parsed custom payload, which you added to the notification in the dashboard
     * @param messageData RemoteMessage payload data
     * @return a map with custom payload from the notification or null if it's not provided.
     */
    fun getNotificationCustomPayload(messageData: Map<String, String>): Map<String, Any?>?

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
    fun setEntitlementsUpdateListener(entitlementsUpdateListener: EntitlementsUpdateListener)
}
