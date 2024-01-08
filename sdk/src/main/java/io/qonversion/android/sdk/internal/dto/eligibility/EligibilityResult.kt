package io.qonversion.android.sdk.internal.dto.eligibility

import io.qonversion.android.sdk.dto.eligibility.QEligibility
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class EligibilityResult(
    @Json(name = "products_enriched") val productsEligibility: Map<String, QEligibility>
)
