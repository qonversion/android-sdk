package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

class GoogleBillingConsumerImpl(private val billingClient: BillingClient) : GoogleBillingConsumer {
    override fun consume(purchaseToken: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.consumeAsync(params) { billingResult, _ ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw QonversionException(
                    ErrorCode.Consuming,
                    getExceptionDetails(purchaseToken, billingResult.responseCode)
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
                    getExceptionDetails(purchaseToken, billingResult.responseCode)
                )
            }
        }
    }

    private fun getExceptionDetails(
        token: String,
        @BillingClient.BillingResponseCode errorCode: Int
    ): String {
        return "Purchase token: \"$token\", google billing error code: \"${errorCode.getDescription()}\""
    }
}
