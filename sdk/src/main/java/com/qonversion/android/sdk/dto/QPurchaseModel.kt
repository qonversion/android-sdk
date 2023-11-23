package com.qonversion.android.sdk.dto

data class QPurchaseModel(
    val qonversionProductId: String,
    var offerId: String? = null
) {
    internal var withoutOffer = false

    fun removeOffer(): QPurchaseModel = apply {
        withoutOffer = true
    }
}
