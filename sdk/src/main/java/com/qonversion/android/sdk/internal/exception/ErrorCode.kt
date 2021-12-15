package com.qonversion.android.sdk.internal.exception

internal enum class ErrorCode(val defaultMessage: String) {
    BadNetworkRequest("Network request is incorrect and thus cannot be executed"),
    NetworkRequestExecution("Failed to execute network request"),
    Serialization("Failed to serialize data"),
    Deserialization("Failed to deserialize data"),
    RequestDenied("Request denied"),
    Mapping("Failed to map response"),
    BadResponse("API response can not be handled"),
    Consuming("Failed to consume purchase in Google Billing"),
    Acknowledging("Failed to acknowledge purchase in Google Billing"),
    Purchasing("Failed to make purchase"),
    PurchasesFetching("Failed to retrieve purchase"),
    PurchasesHistoryFetching("Failed to retrieve purchases history"),
    SkuDetailsFetching("Failure to retrieve SkuDetails")
}
