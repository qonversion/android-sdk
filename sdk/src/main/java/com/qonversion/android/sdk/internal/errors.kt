package com.qonversion.android.sdk.internal

import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.billing.BillingError
import org.json.JSONException
import java.io.IOException

internal fun BillingError.toQonversionError(): QonversionError {
    val errorCode = when (this.billingResponseCode) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR -> QonversionErrorCode.PlayStoreError

        BillingClient.BillingResponseCode.NETWORK_ERROR -> QonversionErrorCode.NetworkConnectionFailed
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> QonversionErrorCode.FeatureNotSupported
        BillingClient.BillingResponseCode.OK -> QonversionErrorCode.UnknownError
        BillingClient.BillingResponseCode.USER_CANCELED -> QonversionErrorCode.PurchaseCanceled

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> QonversionErrorCode.BillingUnavailable
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> QonversionErrorCode.StoreProductNotAvailable

        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> QonversionErrorCode.PurchaseInvalid
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> QonversionErrorCode.ProductAlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> QonversionErrorCode.ProductNotOwned
        else -> QonversionErrorCode.UnknownError
    }
    val additionalMessage = when (errorCode) {
        QonversionErrorCode.BillingUnavailable ->
            "Billing service is not connected to any Google account at the moment."
        QonversionErrorCode.PurchaseInvalid ->
            "Please make sure that you are using the google account where purchases are allowed and the application was correctly signed and properly set up for billing."
        else -> ""
    }

    return QonversionError(errorCode, "${this.message}. $additionalMessage")
}

internal fun Throwable.toQonversionError(): QonversionError {
    return when (this) {
        is JSONException -> {
            QonversionError(QonversionErrorCode.ResponseParsingFailed, localizedMessage ?: "")
        }

        is IOException -> {
            QonversionError(QonversionErrorCode.NetworkConnectionFailed, localizedMessage ?: "")
        }

        else -> QonversionError(QonversionErrorCode.UnknownError, localizedMessage ?: "")
    }
}
