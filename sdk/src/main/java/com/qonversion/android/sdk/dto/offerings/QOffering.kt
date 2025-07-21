package com.qonversion.android.sdk.dto.offerings

import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.equalsIgnoreOrder
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class QOffering(
    @Json(name = "id") val offeringId: String,
    @Json(name = "tag") val tag: QOfferingTag,
    @Json(name = "products") val products: List<QProduct> = listOf()
) {

    fun productForId(id: String): QProduct? {
        return products.firstOrNull { it.qonversionId == id }
    }

    override fun hashCode(): Int {
        return offeringId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is QOffering &&
                other.offeringId == offeringId &&
                other.tag == tag &&
                other.products equalsIgnoreOrder products
    }

    override fun toString(): String {
        return "QOffering(offeringId=$offeringId, tag=$tag, products=$products)"
    }
}
