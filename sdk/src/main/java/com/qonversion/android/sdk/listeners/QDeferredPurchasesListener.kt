package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.QDeferredTransaction

/**
 * The listener for deferred purchase completions.
 *
 * Deferred purchases happen when transactions require additional steps to complete,
 * such as pending transactions on Google Play or purchases requiring additional access grants.
 * This listener provides full transaction details, including for consumable products
 * without associated entitlements.
 *
 * It can be provided to the [QonversionConfig](com.qonversion.android.sdk.QonversionConfig)
 * via [QonversionConfig.Builder.setDeferredPurchasesListener](com.qonversion.android.sdk.QonversionConfig.Builder.setDeferredPurchasesListener)
 * or set directly to the current [Qonversion](com.qonversion.android.sdk.Qonversion) instance
 * via [Qonversion.setDeferredPurchasesListener](com.qonversion.android.sdk.Qonversion.setDeferredPurchasesListener).
 */
interface QDeferredPurchasesListener {

    /**
     * Called when a deferred purchase completes.
     *
     * @param transaction the completed deferred transaction with full details
     *                    including product ID, transaction ID, type, value, and currency.
     */
    fun deferredPurchaseCompleted(transaction: QDeferredTransaction)
}
