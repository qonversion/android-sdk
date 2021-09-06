package com.qonversion.android.sdk.dto.eligibility

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StoreProductInfo(
    @Json(name = "store_id") val storeId: String
)
