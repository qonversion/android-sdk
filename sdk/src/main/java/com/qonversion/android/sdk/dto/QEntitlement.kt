package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.billing.toBoolean
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QEntitlement(
    @Json(name = "id") val permissionID: String,
    @Json(name = "product_id") val productID: String,
//    @Json(name = "renew_state") val renewState: QProductRenewState,
    @Json(name = "started") val startedDate: Date,
    @Json(name = "expires") val expirationDate: Date?,
    @Json(name = "active") internal val active: Int
) {
    fun isActive(): Boolean {
        return active.toBoolean()
    }
}
