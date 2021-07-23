package com.qonversion.android.sdk

import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.nio.charset.Charset

class BackendErrorMapper {
    fun <T> getErrorFromResponse(value: Response<T>): QonversionError {
        var errorMessage = String()
        var code: Int? = null

        value.errorBody()?.let {
            try {
                val responseBodyStr = convertResponseBodyToStr(it)
                val errorObj = JSONObject(responseBodyStr)

                if (errorObj.has(DATA)) {
                    val dataObj = errorObj.getJsonObject(DATA)
                    errorMessage = dataObj.toFormatString(DATA)
                    code = dataObj.getInt(CODE)
                }
                if (errorObj.has(ERROR)) {
                    errorMessage = errorObj.getErrorMessage(ERROR)
                }
                if (errorObj.has(META)) {
                    errorMessage += errorObj.getErrorMessage(META)
                }
            } catch (e: JSONException) {
                errorMessage = "$ERROR=failed to parse the backend response"
            }
        }

        val qonversionCode = getQonversionErrorCode(code)
        val additionalErrorMessage = getAdditionalMessageForCode(code)

        return QonversionError(
            qonversionCode ?: QonversionErrorCode.BackendError,
            "HTTP status code=${value.code()}, $errorMessage. ${additionalErrorMessage ?: ""}"
        )
    }

    private fun convertResponseBodyToStr(response: ResponseBody): String {
        val source = response.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer
        val responseBodyStr = buffer.clone().readString(Charset.forName("UTF-8"))

        return responseBodyStr
    }

    @Throws(JSONException::class)
    private fun JSONObject.getJsonObject(field: String): JSONObject? {
        if (isNull(field)) {
            return null
        }

        return getJSONObject(field)
    }

    @Throws(JSONException::class)
    private fun JSONObject?.getInt(field: String): Int? {
        if (this == null || isNull(field)) {
            return null
        }

        return getInt(field)
    }

    @Throws(JSONException::class)
    private fun JSONObject.getErrorMessage(field: String): String {
        val value = getJsonObject(field)
        return value.toFormatString(field)
    }

    private fun JSONObject?.toFormatString(fieldName: String): String {
        return if (this == null) {
            ""
        } else "$fieldName=${this}"
    }

    private fun getQonversionErrorCode(value: Int?): QonversionErrorCode? {
        val qonversionErrorCode = when (value) {
            10002, 10003 -> QonversionErrorCode.InvalidCredentials
            10004, 10005, 20014 -> QonversionErrorCode.InvalidClientUid
            10006 -> QonversionErrorCode.UnknownClientPlatform
            10008 -> QonversionErrorCode.FraudPurchase
            20005 -> QonversionErrorCode.FeatureNotSupported
            20006, 20007, 20300, 20303 -> QonversionErrorCode.PlayStoreError
            20008, 20010, 20203, 20210 -> QonversionErrorCode.PurchaseInvalid
            20011, 20012, 20013 -> QonversionErrorCode.ProjectConfigError
            20201 -> QonversionErrorCode.InvalidStoreCredentials
            20399 -> QonversionErrorCode.UnknownError
            else -> null
        }

        return qonversionErrorCode
    }

    private fun getAdditionalMessageForCode(value: Int?): String? {
        val qonversionErrorMessage = when (value) {
            20201 -> "This account does not have access to the requested application"
            20203 -> "Possible reasons for the error are fraud purchases and incorrect configuration of the project key on the Qonversion Dashboard."
            else -> null
        }

        return qonversionErrorMessage
    }

    companion object {
        private const val DATA = "data"
        private const val ERROR = "error"
        private const val META = "_meta"
        private const val CODE = "code"
    }
}