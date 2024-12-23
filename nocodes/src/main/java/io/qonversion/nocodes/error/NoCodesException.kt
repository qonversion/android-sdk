package io.qonversion.nocodes.error

class NoCodesException(
    val code: ErrorCode,
    details: String? = null,
    cause: Throwable? = null
) : Exception(details, cause) {

    constructor(error: NoCodesError) : this(error.code, error.details)

    /**
     * Call this function to get prettified exception info
     */
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("Qonversion NoCodesException: ${code.defaultMessage}.")
        message?.let {
            builder.append(" $message.")
        }
        return builder.toString()
    }
}