package com.qonversion.android.sdk.internal.exception

enum class ErrorCode(val defaultMessage: String) {
    NETWORK_REQUEST_EXECUTION("Failed to execute network request"),
    SERIALIZATION("Failed to serialize data"),
    DESERIALIZATION("Failed to deserialize data")
}