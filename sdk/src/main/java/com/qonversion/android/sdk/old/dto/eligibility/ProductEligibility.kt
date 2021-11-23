package com.qonversion.android.sdk.old.dto.eligibility

import com.qonversion.android.sdk.old.dto.products.QProduct
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProductEligibility(
    @Json(name = "product") val product: QProduct,
    @Json(name = "intro_eligibility_status") val eligibilityStatus: QIntroEligibilityStatus
)
