package com.qonversion.android.sdk.billing

class PurchaseError(
    private val code: Int,
    private val underlyingErrorMessage: String
) {
    override fun toString(): String {
        return "PurchaseError (code=$code, underlyingErrorMessage=$underlyingErrorMessage)"
    }
}

