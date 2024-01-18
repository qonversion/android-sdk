package com.qonversion.android.sdk.internal.dto

import com.qonversion.android.sdk.dto.entitlements.QEntitlementGrantType
import com.qonversion.android.sdk.dto.entitlements.QEntitlementSource
import com.qonversion.android.sdk.dto.entitlements.QTransaction
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
    @Json(name = "active") internal val active: Int,
    @Json(name = "renews_count") val renewsCount: Int = 0,
    @Json(name = "trial_start_timestamp") val trialStartDate: Date?,
    @Json(name = "first_purchase_timestamp") val firstPurchaseDate: Date?,
    @Json(name = "last_purchase_timestamp") val lastPurchaseDate: Date?,
    @Json(name = "last_activated_offer_code") val lastActivatedOfferCode: String?,
    @Json(name = "grant_type") val grantType: QEntitlementGrantType = QEntitlementGrantType.Purchase,
    @Json(name = "auto_renew_disable_timestamp") val autoRenewDisableDate: Date?,
    @Json(name = "store_transactions") val transactions: List<QTransaction> = emptyList()
) {
    fun isActive(): Boolean {
        return active.toBoolean()
    }
}
