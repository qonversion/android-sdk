package com.qonversion.android.sdk.dto.offerings

import com.qonversion.android.sdk.dto.products.QProduct
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QOffering (
    @Json(name = "id") val offeringID: String,
    @Json(name = "tag") val tag: QOfferingTag,
    @Json(name = "products") val products: List<QProduct> = listOf()
) {
    fun productForID(id: String): QProduct? {
        return products.firstOrNull { it.qonversionID == id }
    }
}