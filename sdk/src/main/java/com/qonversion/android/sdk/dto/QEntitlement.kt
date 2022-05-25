package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.billing.toBoolean
import com.qonversion.android.sdk.dto.products.QProductRenewState
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QEntitlement(
    @Json(name = "id") val permissionID: String,
    @Json(name = "started") val startedDate: Date,
    @Json(name = "expires") val expirationDate: Date?,
    @Json(name = "active") internal val active: Int,
    @Json(name = "product") val product: Product
) {
    fun isActive(): Boolean {
        return active.toBoolean()
    }

    fun toPermission(): QPermission = QPermission(
        permissionID,
        product.productID,
        product.subscription.renewState,
        startedDate,
        expirationDate,
        active
    )

    data class Product(
        @Json(name = "product_id") val productID: String,
        @Json(name = "subscription") val subscription: Subscription
    ) {
        data class Subscription(
            @Json(name = "renew_state") val renewState: QProductRenewState
        )
    }
}
