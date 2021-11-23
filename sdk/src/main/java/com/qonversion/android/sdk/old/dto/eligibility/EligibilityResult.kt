package com.qonversion.android.sdk.old.dto.eligibility

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EligibilityResult(
    @Json(name = "products_enriched") val productsEligibility: Map<String, QEligibility>
)
