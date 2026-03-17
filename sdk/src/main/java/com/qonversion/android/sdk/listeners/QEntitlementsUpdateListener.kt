package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.entitlements.QEntitlement

/**
 * The listener of user entitlements updates.
 *
 * @deprecated Use [QDeferredPurchasesListener] instead. It provides full transaction details
 * including product ID, transaction ID, and value - critical for consumable products
 * without entitlements.
 */
@Deprecated(
    "Use QDeferredPurchasesListener instead",
    ReplaceWith("QDeferredPurchasesListener")
)
interface QEntitlementsUpdateListener {

    /**
     * Called when user entitlements are updated asynchronously. For example when the purchase is made
     * with SCA or parental control and thus needs additional confirmation.
     *
     * @param entitlements all the current entitlements of the user.
     */
    fun onEntitlementsUpdated(entitlements: Map<String, QEntitlement>)
}
