package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.listeners.QDeferredPurchasesListener
import com.qonversion.android.sdk.listeners.QEntitlementsUpdateListener

/**
 * Adapter that wraps a deprecated [QEntitlementsUpdateListener] as a [QDeferredPurchasesListener].
 *
 * When the deprecated setEntitlementsUpdateListener() is called, this adapter allows
 * QProductCenterManager to work with a single listener type internally
 * (QDeferredPurchasesListener only), eliminating duplicate invocation logic.
 *
 * The adapter extracts entitlements from the [QPurchaseResult] and forwards them
 * to the wrapped legacy listener via onEntitlementsUpdated().
 */
internal class EntitlementsUpdateListenerAdapter(
    private val legacyListener: QEntitlementsUpdateListener
) : QDeferredPurchasesListener {

    override fun deferredPurchaseCompleted(purchaseResult: QPurchaseResult) {
        legacyListener.onEntitlementsUpdated(purchaseResult.entitlements)
    }
}
