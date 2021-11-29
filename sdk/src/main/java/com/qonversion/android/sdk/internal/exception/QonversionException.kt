package com.qonversion.android.sdk.internal.exception

import java.lang.StringBuilder

class QonversionException(
    val code: ErrorCode,
    additionalMessage: String? = null,
    cause: Throwable? = null
): Exception(additionalMessage, cause) {

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("QonversionException: ${code.defaultMessage}.")
        message?.let {
            builder.append(" $message.")
        }
        return builder.toString()
    }
}