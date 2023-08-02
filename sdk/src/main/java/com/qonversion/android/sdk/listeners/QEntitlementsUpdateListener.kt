package com.qonversion.android.sdk.listeners

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
    fun onEntitlementsUpdated(entitlements: Map<String, QEntitlement>)
}
