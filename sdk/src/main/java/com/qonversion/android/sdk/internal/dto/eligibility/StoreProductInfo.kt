package com.qonversion.android.sdk.internal.dto.eligibility

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class StoreProductInfo(
    @Json(name = "store_id") val storeId: String
)
