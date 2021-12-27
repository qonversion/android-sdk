package com.qonversion.android.sdk.internal.billing.dto

import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

internal data class BillingError(
    @BillingClient.BillingResponseCode val billingResponseCode: Int,
    val message: String
) {
    fun toQonversionException(): QonversionException {
        val errorCode = when (billingResponseCode) {
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR -> ErrorCode.PlayStore
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> ErrorCode.FeatureNotSupported
            BillingClient.BillingResponseCode.USER_CANCELED -> ErrorCode.CanceledPurchase
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> ErrorCode.BillingUnavailable
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> ErrorCode.ProductUnavailable
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> ErrorCode.Purchasing
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> ErrorCode.ProductAlreadyOwned
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> ErrorCode.ProductNotOwned
            else -> ErrorCode.Unknown
        }
        val additionalMessage = when (errorCode) {
            ErrorCode.BillingUnavailable ->
                "Billing service is not connected to any Google account at the moment."
            ErrorCode.Purchasing ->
                "Please make sure that you are using the google account where purchases are allowed " +
                        "and the application was correctly signed and properly set up for billing."
            else -> ""
        }

        return QonversionException(errorCode, additionalMessage)
    }
}
