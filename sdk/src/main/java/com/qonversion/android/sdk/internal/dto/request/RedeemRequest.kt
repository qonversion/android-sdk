package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for `POST /v4/web/redeem` — exchanges a Web2App token issued
 * by `/v4/web/payment` for an entitlement grant attached to the current SDK user.
 *
 * The default restore behavior is `Transfer` (see plan §"DEV-845"), which is
 * enforced server-side; the field is included here so the SDK can override it
 * in future iterations without an API change.
 */
@JsonClass(generateAdapter = true)
internal data class RedeemRequest(
    @Json(name = "token") val token: String,
    // Web2App M1.5 canonical contract: api-gateway and purchaseman read
    // "app_uid". The value is unchanged (the SDK user id / internalConfig.uid);
    // only the wire key was renamed from the legacy "anon_user_id".
    @Json(name = "app_uid") val appUid: String?,
    @Json(name = "restore_behavior") val restoreBehavior: String = "transfer",
)

@JsonClass(generateAdapter = true)
internal data class RedeemStatusRequest(
    @Json(name = "token") val token: String,
)

@JsonClass(generateAdapter = true)
internal data class RedeemReissueRequest(
    @Json(name = "email") val email: String,
)
