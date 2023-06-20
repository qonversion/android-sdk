package com.qonversion.android.sdk.dto.offerings

import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.equalsIgnoreOrder
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class QOffering(
    @Json(name = "id") val offeringID: String,
    @Json(name = "tag") val tag: QOfferingTag,
    @Json(name = "products") val products: List<QProduct> = listOf()
) {

    fun productForID(id: String): QProduct? {
        return products.firstOrNull { it.qonversionID == id }
    }

    override fun hashCode(): Int {
        return offeringID.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is QOffering &&
                other.offeringID == offeringID &&
                other.tag == tag &&
                other.products equalsIgnoreOrder products
    }

    override fun toString(): String {
        return "QOffering(offeringID=$offeringID, tag=$tag, products=$products)"
    }
}
