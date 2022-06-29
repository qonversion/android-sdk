package com.qonversion.android.sdk.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PurchaseRequest(
    @Json(name = "currency") val currency: String,
    @Json(name = "price") val price: String,
    @Json(name = "purchased") val purchased: Int,
    @Json(name = "play_store_data") val playStoreData: PlayStoreData
)

@JsonClass(generateAdapter = true)
data class PlayStoreData(
    @Json(name = "type") val type: UserPurchaseProductType,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "purchase_token") val purchaseToken: String,
    @Json(name = "product_id") val productId: String,
    @Json(name = "subscription_period") val subscriptionPeriod: String?,
    @Json(name = "free_trial_period") val freeTrialPeriod: String?
)

enum class UserPurchaseProductType(val apiKey: String) {
    Subscription("subscription"),
    NonRecurring("non_recurring");

    companion object {
        fun fromKey(key: String) = values().find { it.apiKey == key }
    }
}

@JsonClass(generateAdapter = true)
data class UserPurchase(
    @Json(name = "user_id") val userId: String,
    @Json(name = "currency") val currency: String?, // Currency code by ISO 4217 standard
    @Json(name = "price") val price: String?,
    @Json(name = "purchased") val purchased: Int?, // The UNIX time, in milliseconds
    @Json(name = "play_store_data") val playStoreData: PlayStoreData
)
