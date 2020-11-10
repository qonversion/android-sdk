package com.qonversion.android.sdk

class QonversionError(
    val code: QonversionErrorCode,
    val additionalMessage: String = ""
) {
    val description: String = code.specification

    override fun toString(): String {
        return "QonversionError: {code=$code, description=$description, additionalMessage=$additionalMessage}"
    }
}

enum class QonversionErrorCode(val specification: String) {
    UnknownError("Unknown error"),
    PlayStoreError("There was an issue with Play Store service"),
    BillingUnavailable("Billing API version is not supported for the type requested"),
    PurchaseInvalid("Invalid arguments provided to the Billing API"),
    CanceledPurchase("User pressed back or canceled a dialog for purchase"),
    ProductNotOwned("Failure to consume purchase since item is not owned"),
    ProductAlreadyOwned("Failure to purchase since item is already owned"),
    FeatureNotSupported("Requested feature is not supported by Play Store on the current device"),
    ProductUnavailable("Requested product is not available for purchase"),
    NetworkConnectionFailed("There was a network issue"),
    ParseResponseFailed("A problem occurs when serializing or deserializing data"),
    BackendError("There was a backend error"),
    ProductNotFound("Failure to purchase since product not found"),
    LaunchError("There was a launch error"),
    SkuDetailsError("There was a SkuDetails error. Please be sure SkuDetails configured correctly")
}