package com.qonversion.android.sdk.internal.dto.redemption

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response body for `POST /v4/web/redeem` on success (HTTP 200).
 *
 * `user_id` is the Qonversion user id the entitlement was granted to. The SDK
 * calls `identify(user_id)` after a successful redemption to merge the current
 * anon session with that account (RT4-W2 / plan §"DEV-847").
 */
@JsonClass(generateAdapter = true)
internal data class RedeemResponse(
    @Json(name = "user_id") val userId: String?,
)

/**
 * Response body for `POST /v4/web/redeem/status` — polled after a 409 from
 * `/v4/web/redeem` to determine whether the token was already consumed (in
 * which case the host shows recovery UI suggestion).
 */
@JsonClass(generateAdapter = true)
internal data class RedeemStatusResponse(
    @Json(name = "consumed") val consumed: Boolean = false,
    @Json(name = "expired") val expired: Boolean = false,
)
