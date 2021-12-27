package com.qonversion.android.sdk.internal.billing.consumer

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger

internal class GoogleBillingConsumerImpl(logger: Logger) : BaseClass(logger), GoogleBillingConsumer {

    private lateinit var billingClient: BillingClient

    override fun setup(billingClient: BillingClient) {
        this.billingClient = billingClient
    }

    override fun consume(purchaseToken: String) {
        logger.debug("consume() -> Consuming purchase with token $purchaseToken")

        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.consumeAsync(params) { billingResult, handledPurchaseToken ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw QonversionException(
                    ErrorCode.Consuming,
                    getExceptionDetails(handledPurchaseToken, billingResult)
                )
            }
        }
    }

    override fun acknowledge(purchaseToken: String) {
        logger.debug("acknowledge() -> Acknowledging purchase with token $purchaseToken")

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw QonversionException(
                    ErrorCode.Acknowledging,
                    getExceptionDetails(purchaseToken, billingResult)
                )
            }
        }
    }

    private fun getExceptionDetails(
        token: String,
        billingResult: BillingResult
    ): String {
        return "Purchase token: \"$token\". ${billingResult.getDescription()}"
    }
}
