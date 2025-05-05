package io.qonversion.nocodes.error

/**
 * This enum contains all available values of ErrorCode that QonversionException may contain.
 */
enum class ErrorCode(val defaultMessage: String) {
    ActivityStart("Failed to start activity for No-Code screen"),
    BadNetworkRequest("Network request is incorrect and thus cannot be executed"),
    NetworkRequestExecution("Failed to execute network request"),
    Serialization("Failed to serialize data"),
    Deserialization("Failed to deserialize data"),
    RequestDenied("Request denied"),
    Mapping("Failed to map response"),
    BadResponse("API response can not be handled"),
    BackendError("Qonversion API returned an error"),
    ScreenNotFound("No-Code screen not found"),
    QonversionError("An internal error from Qonversion SDK. For more details look at the nested error."),
}
