package com.qonversion.android.sdk.internal.exception

import java.lang.StringBuilder

/**
 * Qonversion exception that SDK may throw inside a throwable functions
 * Check error code and details to get more information about concrete exception you handle
 * @property code an instance of ErrorCode
 * @property details a detailed description of the exception
 * @property cause a reason of the exception
 */
class QonversionException(
    val code: ErrorCode,
    details: String? = null,
    cause: Throwable? = null
) : Exception(details, cause) {

    /**
     * Call this function to get prettified exception info
     */
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("QonversionException: ${code.defaultMessage}.")
        message?.let {
            builder.append(" $message.")
        }
        return builder.toString()
    }
}
