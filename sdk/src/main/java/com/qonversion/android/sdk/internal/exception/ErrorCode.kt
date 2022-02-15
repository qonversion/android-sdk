package com.qonversion.android.sdk.internal.exception

/**
 * This enum contains all available values of ErrorCode that QonversionException may contain.
 */
enum class ErrorCode(val defaultMessage: String) {
    BadNetworkRequest("Network request is incorrect and thus cannot be executed"),
    NetworkRequestExecution("Failed to execute network request"),
    Serialization("Failed to serialize data"),
    Deserialization("Failed to deserialize data"),
    RequestDenied("Request denied"),
    Mapping("Failed to map response"),
    BadResponse("API response can not be handled"),
    BackendError("Qonversion API returned an error"),
    UserNotFound("Qonversion user not found"),
    BillingConnection("Failed to connect to BillingClient"),
    Consuming("Failed to consume purchase in Google Billing"),
    Acknowledging("Failed to acknowledge purchase in Google Billing"),
    Purchasing("Failed to make purchase"),
    PurchasesFetching("Failed to retrieve purchase"),
    PurchasesHistoryFetching("Failed to retrieve purchases history"),
    SkuDetailsFetching("Failed to retrieve SkuDetails"),
    PlayStore("There was an issue with the Play Store service"),
    FeatureNotSupported("The requested feature is not supported"),
    CanceledPurchase("User pressed back or canceled a dialog for purchase"),
    BillingUnavailable("The Billing service is unavailable on the device"),
    ProductUnavailable("Requested product is not available for purchase or its SKU was not found"),
    ProductAlreadyOwned("Failed to purchase since item is already owned"),
    ProductNotOwned("Failed to consume purchase since item is not owned"),
    Unknown("Unknown error"),
    NotInitialized("Qonversion has not been initialized. You should call " +
            "the initialize method before accessing the shared instance of Qonversion."),
    ConfigPreparation("Failed to prepare configuration for SDK initialization"),
    UserInfoIsMissing("Failed to retrieve user user info")
}
