package com.qonversion.android.sdk.dto.offerings

import com.qonversion.android.sdk.internal.OfferingsDelegate
import com.qonversion.android.sdk.dto.experiments.QExperimentInfo
import com.qonversion.android.sdk.dto.products.QProduct
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class QOffering(
    @Json(name = "id") val offeringID: String,
    @Json(name = "tag") val tag: QOfferingTag,
    @Json(name = "products") products: List<QProduct> = listOf(),
    @Json(name = "experiment") val experimentInfo: QExperimentInfo? = null
) {
    @Transient
    internal var observer: OfferingsDelegate? = null

    val products: List<QProduct> = products
        get() {
            observer?.offeringByIDWasCalled(this)
            return field
        }

    fun productForID(id: String): QProduct? {
        return products.firstOrNull { it.qonversionID == id }
    }
}
