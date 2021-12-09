package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

internal class GoogleBillingConsumerImpl(private val billingClient: BillingClient) : GoogleBillingConsumer {
    override fun consume(purchaseToken: String) {
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
