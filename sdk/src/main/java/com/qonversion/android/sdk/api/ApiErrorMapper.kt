package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionErrorCode
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

class ApiErrorMapper @Inject constructor(private val helper: ApiHelper) {
    fun <T> getErrorFromResponse(value: Response<T>): QonversionError {
        var errorMessage = String()
        var code: Int? = null

        value.errorBody()?.let {
            try {
                val responseBodyStr = convertResponseBody(it)
                val responseObj = JSONObject(responseBodyStr)

                val request = value.raw().request()
                if (helper.isV1Request(request)) {
                    val dataObj = responseObj.getJsonObject(DATA)
                    dataObj.toFormatString(DATA)?.let { errStr ->
                        errorMessage = errStr
                    }

                    code = dataObj.getInt(CODE)
                } else {
                    val errorObj = responseObj.getJsonObject(ERROR)

                    val errStr = errorObj.getString(MESSAGE)
                    errStr.toFormatString(ERROR)?.let { formatErrStr ->
                        errorMessage = formatErrStr
                    }
                }
            } catch (e: JSONException) {
                errorMessage = "$ERROR=failed to parse the backend response"
            } catch (e: IOException) {
                errorMessage = "$ERROR=${e.localizedMessage}"
            }
        }

        val qonversionCode = getQonversionErrorCode(code)
        val additionalErrorMessage = getAdditionalMessageForCode(code)

        return QonversionError(
            qonversionCode,
            "HTTP status code=${value.code()}, $errorMessage. ${additionalErrorMessage ?: ""}"
        )
    }

    @Throws(IOException::class)
    private fun convertResponseBody(response: ResponseBody): String {
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
    private fun JSONObject?.getString(field: String): String? {
        if (this == null || isNull(field)) {
            return null
        }

        return getString(field)
    }

    private fun Any?.toFormatString(fieldName: String): String? {
        return if (this == null) {
            null
        } else "$fieldName=${this}"
    }

    private fun getQonversionErrorCode(value: Int?): QonversionErrorCode {
        val qonversionErrorCode = when (value) {
            10002, 10003 -> QonversionErrorCode.InvalidCredentials
            10004, 10005, 20014 -> QonversionErrorCode.InvalidClientUid
            10006 -> QonversionErrorCode.UnknownClientPlatform
            10008 -> QonversionErrorCode.FraudPurchase
            20005 -> QonversionErrorCode.FeatureNotSupported
            20006, 20007, 20300, 20303, 20399 -> QonversionErrorCode.PlayStoreError
            20008, 20010, 20203, 20210 -> QonversionErrorCode.PurchaseInvalid
            20011, 20012, 20013 -> QonversionErrorCode.ProjectConfigError
            20201 -> QonversionErrorCode.InvalidStoreCredentials
            else -> QonversionErrorCode.BackendError
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
        private const val CODE = "code"
        private const val MESSAGE = "message"
    }
}