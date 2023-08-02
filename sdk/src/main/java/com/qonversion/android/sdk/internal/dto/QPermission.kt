package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.internal.toBoolean
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
internal data class QPermission(
    @Json(name = "id") val permissionID: String,
    @Json(name = "associated_product") val productID: String,
    @Json(name = "renew_state") val renewState: QProductRenewState,
    @Json(name = "started_timestamp") val startedDate: Date,
    @Json(name = "expiration_timestamp") val expirationDate: Date?,
    @Json(name = "source") val source: QEntitlementSource = QEntitlementSource.Unknown,
    @Json(name = "active") internal val active: Int
) {
    fun isActive(): Boolean {
        return active.toBoolean()
    }
}
