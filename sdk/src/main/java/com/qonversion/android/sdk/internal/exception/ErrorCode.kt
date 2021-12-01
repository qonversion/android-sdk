package com.qonversion.android.sdk.internal.exception

internal enum class ErrorCode(val defaultMessage: String) {
    NetworkRequestExecution("Failed to execute network request"),
    Serialization("Failed to serialize data"),
    Deserialization("Failed to deserialize data"),
    RequestDenied("Request denied"),
    Mapping("Failed to map response")
}
