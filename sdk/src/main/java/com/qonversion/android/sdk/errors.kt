package com.qonversion.android.sdk

import com.android.billingclient.api.BillingClient
import com.qonversion.android.sdk.billing.BillingError
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

fun BillingError.toQonversionError(): QonversionError {
    val errorCode = when (this.billingResponseCode) {
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR -> QonversionErrorCode.PlayStoreError

        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> QonversionErrorCode.FeatureNotSupported
        BillingClient.BillingResponseCode.OK -> QonversionErrorCode.UnknownError
        BillingClient.BillingResponseCode.USER_CANCELED -> QonversionErrorCode.CanceledPurchase

        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> QonversionErrorCode.BillingUnavailable
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> QonversionErrorCode.ProductUnavailable

        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> QonversionErrorCode.PurchaseInvalid
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> QonversionErrorCode.ProductAlreadyOwned
        BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> QonversionErrorCode.ProductNotOwned
        else -> QonversionErrorCode.UnknownError
    }

    return QonversionError(errorCode, this.message)
}

fun Throwable.toQonversionError(): QonversionError {
    return when (this) {
        is JSONException ->{
            QonversionError(QonversionErrorCode.ParseResponseFailed, localizedMessage ?: "")
        }

        is IOException -> {
            QonversionError( QonversionErrorCode.NetworkConnectionFailed,  localizedMessage ?: "")
        }

        else -> QonversionError(QonversionErrorCode.UnknownError, localizedMessage ?: "")
    }
}

fun <T> Response<T>.toQonversionError(): QonversionError {
    val data = "data"
    val error = "error"
    val meta = "_meta"
    var errorMessage = String()

    errorBody()?.let {
        try {
            val errorObj = JSONObject(it.string())

            if (errorObj.has(data)) {
                errorMessage = errorObj.getErrorMessage(data)
            }
            if (errorObj.has(error)) {
                errorMessage = errorObj.getErrorMessage(error)
            }
            if (errorObj.has(meta)) {
                errorMessage += errorObj.getErrorMessage(meta)
            }
        } catch (e: JSONException) {
            errorMessage = formatError(error, "failed to parse the backend response")
        }
    }

    return QonversionError(QonversionErrorCode.BackendError, "HTTP status code=${this.code()}$errorMessage")
}

private fun formatError(name: String, value: String) = String.format(", %s=%s", name, value)

private fun JSONObject.getErrorMessage(field: String) = formatError(
    field,
    getJSONObject(field).toString()
)