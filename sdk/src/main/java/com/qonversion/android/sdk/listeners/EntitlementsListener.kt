package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.Entitlement

/**
 * The listener of user entitlements changes.
 *
 * It can be provided to the [QonversionConfig](com.qonversion.android.sdk.QonversionConfig)
 * via [QonversionConfig.Builder.setEntitlementsListener](com.qonversion.android.sdk.QonversionConfig.Builder.setEntitlementsListener)
 * or set directly to the current [Qonversion](com.qonversion.android.sdk.Qonversion) instance
 * via [Qonversion.setEntitlementsListener](com.qonversion.android.sdk.Qonversion.setEntitlementsListener).
 */
interface EntitlementsListener {

    /**
     * Called when user entitlements changed asynchronously. For example when the purchase is made
     * with SCA or parental control and thus needs additional confirmation.
     *
     * @param entitlements all the current entitlements of the user.
     */
    fun onEntitlementsChanged(entitlements: Set<Entitlement>)
}
