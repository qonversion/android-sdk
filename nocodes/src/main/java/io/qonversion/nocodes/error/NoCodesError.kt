package io.qonversion.nocodes.error

class NoCodesError(
    val code: ErrorCode,
    val details: String? = null,
) {
    override fun toString(): String {
        return "NoCodesError: {code=$code, description=${code.defaultMessage}, details=${details ?: ""}"
    }
}
