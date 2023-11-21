package com.qonversion.android.sdk.internal.billing

import android.app.Activity
import androidx.annotation.UiThread
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.qonversion.android.sdk.internal.logger.Logger

internal abstract class BillingClientWrapperBase(
    protected val billingClientHolder: BillingClientHolder,
    protected val logger: Logger
) {
    fun consume(purchaseToken: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClientHolder.withReadyClient {
            consumeAsync(
                params
            ) { billingResult, purchaseToken ->
                if (!billingResult.isOk) {
                    val errorMessage =
                        "Failed to consume purchase with token $purchaseToken ${billingResult.getDescription()}"
                    logger.debug("consume() -> $errorMessage")
                }
            }
        }
    }

    fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClientHolder.withReadyClient {
            acknowledgePurchase(
                params
            ) { billingResult ->
                if (!billingResult.isOk) {
                    val errorMessage =
                        "Failed to acknowledge purchase with token $purchaseToken ${billingResult.getDescription()}"
                    logger.debug("acknowledge() -> $errorMessage")
                }
            }
        }
    }

    @UiThread
    protected fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams
    ) = billingClientHolder.withReadyClient {
        launchBillingFlow(activity, params)
            .takeUnless { billingResult -> billingResult.isOk }
            ?.let { billingResult ->
                logger.release("launchBillingFlow() -> Failed to launch billing flow. ${billingResult.getDescription()}")
            }
    }

    protected fun handlePurchasesQueryError(
        billingResult: BillingResult,
        purchaseType: String,
        onQueryFailed: (error: BillingError) -> Unit
    ) {
        val errorMessage =
            "Failed to query $purchaseType purchases from cache: ${billingResult.getDescription()}"
        onQueryFailed(BillingError(billingResult.responseCode, errorMessage))
        logger.release("queryPurchases() -> $errorMessage")
    }
}