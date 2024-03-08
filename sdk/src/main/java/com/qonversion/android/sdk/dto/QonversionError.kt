package com.qonversion.android.sdk.dto

data class QonversionError(
    val code: QonversionErrorCode,
    val additionalMessage: String = ""
) {
    val description: String = code.specification

    override fun toString(): String {
        return "QonversionError: {code=$code, description=$description, additionalMessage=$additionalMessage}"
    }
}

/**
 * For more details on possible error reasons please check our guides:
 * @see [Handling Errors](https://documentation.qonversion.io/docs/handling-errors)
 * @see [Troubleshooting](https://documentation.qonversion.io/docs/troubleshooting)
 * To get rid of billing errors make sure you follow the [Google Play's billing system integration](https://documentation.qonversion.io/docs/google-plays-billing-integration)
 */
enum class QonversionErrorCode(val specification: String) {
    UnknownError("Unknown error"),
    PlayStoreError("There was an issue with the Play Store service"),
    BillingUnavailable("The Billing service is unavailable on the device"),
    PurchasePending("Purchase is pending"),
    PurchaseUnspecified("Unspecified state of the purchase"),
    PurchaseInvalid("Failure of purchase"),
    CanceledPurchase("User pressed back or canceled a dialog for purchase"),
    ProductNotOwned("Failed to consume purchase since item is not owned"),
    ProductAlreadyOwned("Failed to purchase since item is already owned"),
    FeatureNotSupported("The requested feature is not supported"),
    ProductUnavailable("Requested product is not available for purchase or its product id was not found"),
    NetworkConnectionFailed("There was a network issue. " +
            "Please make sure that the Internet connection is available on the device"),
    ParseResponseFailed("A problem occurred while serializing or deserializing data"),
    BackendError("There was a backend error"),
    ProductNotFound("Failed to purchase since the Qonversion product was not found"),
    OfferingsNotFound("No offerings found"),
    LaunchError("There was an error while launching Qonversion SDK"),
    InvalidCredentials("Access token is invalid or not set"),
    InvalidClientUid("Client Uid is invalid or not set"),
    UnknownClientPlatform("The current platform is not supported"),
    FraudPurchase("Fraud purchase was detected"),
    ProjectConfigError("The project is not configured or configured incorrectly in the Qonversion Dashboard"),
    InvalidStoreCredentials("This account does not have access to the requested application"),
    RemoteConfigurationNotAvailable("Remote configuration is not available for the current user or for the provided context key"),
    ApiRateLimitExceeded("API requests rate limit exceeded"),
}
