package com.qonversion.android.sdk.dto.eligibility

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EligibilityResult (
    @Json(name = "products_enriched") val productsEligibility: List<ProductEligibility>
)