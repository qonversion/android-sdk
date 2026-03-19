package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.QDeferredTransaction
import com.qonversion.android.sdk.dto.QPurchaseResult

/**
 * The listener for deferred purchase completions.
 *
 * Deferred purchases happen when transactions require additional steps to complete,
 * such as pending transactions on Google Play or purchases requiring additional access grants.
 * This listener provides full transaction details and the purchase result with entitlements.
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
     * A deferred purchase is fundamentally a purchase, so the callback provides both
     * the transaction details and the full purchase result containing entitlements.
     *
     * @param transaction the completed deferred transaction with full details
     *                    including product ID, transaction ID, type, value, and currency.
     * @param purchaseResult the purchase result containing entitlements granted by this purchase.
     */
    fun deferredPurchaseCompleted(transaction: QDeferredTransaction, purchaseResult: QPurchaseResult)
}
