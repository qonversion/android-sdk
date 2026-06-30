package com.qonversion.android.sdk.internal.dto.redemption

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response body for `POST /v4/web/redeem` on success (HTTP 200).
 *
 * Web2App M1.5 canonical contract: `{ "redeemed": bool, "app_uid": string }`.
 * Under grant-first entitlement the server has already attached the grant to
 * `app_uid`, so the SDK does NOT identify/merge a client user id here — there
 * is intentionally no `user_id` field. On success the SDK only refreshes the
 * device's entitlement state so the server grant is reflected locally.
 */
@JsonClass(generateAdapter = true)
internal data class RedeemResponse(
    @Json(name = "redeemed") val redeemed: Boolean = false,
    @Json(name = "app_uid") val appUid: String? = null,
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
