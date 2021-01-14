package com.qonversion.android.sdk

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
    BillingUnavailable("The Billing service is unavailable on the device because it is not connected to any Google account at the moment"),
    PurchaseInvalid("Failure to purchase. Please make sure that you are using the google account where purchases are allowed and the application was correctly signed and properly set up for billing"),
    CanceledPurchase("User pressed back or canceled a dialog for purchase"),
    ProductNotOwned("Failure to consume purchase since item is not owned"),
    ProductAlreadyOwned("Failure to purchase since item is already owned"),
    FeatureNotSupported("Requested feature is not supported by Play Store on the current device"),
    ProductUnavailable("Requested product is not available for purchase"),
    NetworkConnectionFailed("There was a network issue. Please make sure that the Internet connection is available on the device"),
    ParseResponseFailed("A problem occurred when serializing or deserializing data"),
    BackendError("There was a backend error. Please see additionalMessage for detailed error information"),
    ProductNotFound("Failure to purchase since the Qonversion product was not found"),
    OfferingsNotFound("No offerings found"),
    LaunchError("There was an error on launching Qonversion SDK"),
    SkuDetailsError("Failure to retrieve SkuDetails for the in-app product ID. Please make sure that the products were configured correctly in Google Play Console")
}