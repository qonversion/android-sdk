package com.qonversion.android.sdk.dto

data class QPurchaseUpdateModel(
    val qonversionProductId: String,
    var oldQonversionProductId: String,
    var updatePolicy: QPurchaseUpdatePolicy? = null,
    var offerId: String? = null
)
