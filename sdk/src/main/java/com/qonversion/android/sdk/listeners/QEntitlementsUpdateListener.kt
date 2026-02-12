package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.entitlements.QEntitlement

/**
 * The listener of user entitlements updates.
 *
 * It can be provided to the [QonversionConfig](com.qonversion.android.sdk.QonversionConfig)
 * via [QonversionConfig.Builder.setEntitlementsUpdateListener](com.qonversion.android.sdk.QonversionConfig.Builder.setEntitlementsUpdateListener)
 * or set directly to the current [Qonversion](com.qonversion.android.sdk.Qonversion) instance
 * via [Qonversion.setEntitlementsUpdateListener](com.qonversion.android.sdk.Qonversion.setEntitlementsUpdateListener).
 */
interface QEntitlementsUpdateListener {

    /**
     * Called when user entitlements are updated asynchronously. For example when the purchase is made
     * with SCA or parental control and thus needs additional confirmation.
     *
     * @param entitlements all the current entitlements of the user.
     */
    @Deprecated(
        "Use onEntitlementsUpdated(entitlements, purchaseResult) instead",
        ReplaceWith("onEntitlementsUpdated(entitlements, null)")
    )
    fun onEntitlementsUpdated(entitlements: Map<String, QEntitlement>) {
        onEntitlementsUpdated(entitlements, null)
    }

    /**
     * Called when user entitlements are updated asynchronously. For example when the purchase is made
     * with SCA or parental control and thus needs additional confirmation.
     *
     * For consumable purchases that complete in the background (deferred purchases),
     * entitlements may be empty while [purchaseResult] contains the purchase details.
     *
     * @param entitlements all the current entitlements of the user.
     * @param purchaseResult the purchase result associated with this update, if available.
     *                       This is especially useful for consumable purchases that don't create entitlements.
     */
    fun onEntitlementsUpdated(
        entitlements: Map<String, QEntitlement>,
        purchaseResult: QPurchaseResult?
    ) {
        @Suppress("DEPRECATION")
        onEntitlementsUpdated(entitlements)
    }
}
