package com.qonversion.android.sdk.internal.exception

import java.lang.StringBuilder

/**
 * Qonversion exception that SDK may throw inside a throwable functions
 * Check error code and details to get more information about concrete exception you handle
 */
class QonversionException(
    val code: ErrorCode, // code of exception
    details: String? = null, // details about the reason of exception
    cause: Throwable? = null // initial reason of exception
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
