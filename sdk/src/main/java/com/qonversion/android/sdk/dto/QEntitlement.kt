package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.internal.dto.QProductRenewState
import com.qonversion.android.sdk.internal.toInt
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QEntitlement(
    @Json(name = "id") val permissionID: String,
    @Json(name = "started") val startedDate: Date,
    @Json(name = "expires") val expirationDate: Date?,
    @Json(name = "active") internal val active: Boolean,
    @Json(name = "source") val source: QEntitlementSource,
    @Json(name = "product") val product: Product
) {
    internal constructor(permission: QPermission) : this(
        permission.permissionID,
        permission.startedDate,
        permission.expirationDate,
        permission.isActive(),
        permission.source,
        Product(
            permission.productID,
            Product.Subscription(
                QEntitlementRenewState.fromProductRenewState(permission.renewState)
            )
        )
    )

    val isActive get() = active

    internal fun toPermission(): QPermission = QPermission(
        permissionID,
        product.productID,
        product.subscription?.renewState?.toProductRenewState() ?: QProductRenewState.Unknown,
        startedDate,
        expirationDate,
        source,
        active.toInt()
    )

    @JsonClass(generateAdapter = true)
    data class Product(
        @Json(name = "product_id") val productID: String,
        @Json(name = "subscription") val subscription: Subscription?
    ) {
        @JsonClass(generateAdapter = true)
        data class Subscription(
            @Json(name = "renew_state") val renewState: QEntitlementRenewState
        )
    }
}
