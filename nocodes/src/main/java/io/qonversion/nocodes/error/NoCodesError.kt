package io.qonversion.nocodes.error

import com.qonversion.android.sdk.dto.QonversionError

class NoCodesError(
    val code: ErrorCode,
    val details: String? = null,
    val qonversionError: QonversionError? = null, /** is present, if the [code] is [ErrorCode.QonversionError] */
) {
    constructor(qonversionError: QonversionError) : this(ErrorCode.QonversionError, null, qonversionError)

    constructor(noCodesException: NoCodesException) : this(noCodesException.code, noCodesException.message)

    override fun toString(): String {
        return "NoCodesError: {code=$code, description=${code.defaultMessage}, details=${details ?: ""}"
    }

    companion object {
        fun fromClientThrowable(throwable: Throwable?): NoCodesError {
            return NoCodesError(
                ErrorCode.ClientError,
                throwable?.message ?: throwable?.toString() ?: "Unknown error"
            )
        }
    }
}
