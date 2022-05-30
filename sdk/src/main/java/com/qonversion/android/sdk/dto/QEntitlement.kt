package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QEntitlement(
    @Json(name = "id") val permissionID: String,
    @Json(name = "started") val startedDate: Date,
    @Json(name = "expires") val expirationDate: Date?,
    @Json(name = "active") internal val active: Boolean,
    @Json(name = "product") val product: Product
) {

    fun toPermission(): QPermission = QPermission(
        permissionID,
        product.productID,
        product.subscription.renewState.toProductRenewState(),
        startedDate,
        expirationDate,
        if (active) 1 else 0
    )

    @JsonClass(generateAdapter = true)
    data class Product(
        @Json(name = "product_id") val productID: String,
        @Json(name = "subscription") val subscription: Subscription
    ) {
        @JsonClass(generateAdapter = true)
        data class Subscription(
            @Json(name = "renew_state") val renewState: QEntitlementRenewState
        )
    }
}
