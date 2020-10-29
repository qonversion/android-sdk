package com.qonversion.android.sdk

import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.billing.BillingError
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

fun BillingError.toQonversionError(): QError {
    val errorCode = when (this.billingResponseCode) {
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR -> QErrorCode.PlayStoreError

        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> QErrorCode.FeatureNotSupported
        BillingClient.BillingResponseCode.OK -> QErrorCode.UnknownError
        BillingClient.BillingResponseCode.USER_CANCELED -> QErrorCode.CanceledPurchase

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> QErrorCode.BillingUnavailable
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> QErrorCode.ProductUnavailable

        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> QErrorCode.PurchaseInvalid
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> QErrorCode.ProductAlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> QErrorCode.ProductNotOwned
        else -> QErrorCode.UnknownError
    }

    return QError(errorCode, this.message)
}

fun Throwable.toQonversionError(): QError {
    return when (this) {
        is JSONException ->{
            QError(QErrorCode.ParseResponseFailed, localizedMessage ?: "")
        }

        is IOException -> {
            QError( QErrorCode.NetworkConnectionFailed,  localizedMessage ?: "")
        }

        else -> QError(QErrorCode.UnknownError, localizedMessage ?: "")
    }
}

fun <T> Response<T>.toQonversionError(): QError {
    var errorMessage = ""
    errorBody()?.let {errorBody ->
        val errorBodyJson = JSONObject(errorBody.string())
        if (errorBodyJson.has("data")) {
            val dataJson = errorBodyJson.getJSONObject("data")
            errorMessage += if (dataJson.has("message")) dataJson.getString("message") else ""
        }
    }

    return QError(QErrorCode.BackendError,  "HTTP status code=${this.code()}, errorMessage=$errorMessage")
}