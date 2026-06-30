package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.properties.QUserProperty
import com.qonversion.android.sdk.internal.Constants.CRASH_LOGS_URL
import com.qonversion.android.sdk.internal.dto.eligibility.EligibilityResult
import com.qonversion.android.sdk.internal.dto.identity.IdentityResult
import com.qonversion.android.sdk.internal.dto.BaseResponse
import com.qonversion.android.sdk.internal.dto.Data
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.Response
import com.qonversion.android.sdk.internal.dto.SendPropertiesResult
import com.qonversion.android.sdk.internal.dto.request.AttachUserRequest
import com.qonversion.android.sdk.internal.dto.request.AttributionRequest
import com.qonversion.android.sdk.internal.dto.request.CrashRequest
import com.qonversion.android.sdk.internal.dto.request.EligibilityRequest
import com.qonversion.android.sdk.internal.dto.request.IdentityRequest
import com.qonversion.android.sdk.internal.dto.request.InitRequest
import com.qonversion.android.sdk.internal.dto.request.PurchaseRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemReissueRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemRequest
import com.qonversion.android.sdk.internal.dto.request.RedeemStatusRequest
import com.qonversion.android.sdk.internal.dto.redemption.RedeemResponse
import com.qonversion.android.sdk.internal.dto.redemption.RedeemStatusResponse
import com.qonversion.android.sdk.internal.dto.request.RestoreRequest
import com.qonversion.android.sdk.internal.dto.request.data.UserPropertyRequestData
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

internal interface Api {

    @POST("v1/user/init")
    fun init(
        @Body request: InitRequest,
        @Header("Trigger") trigger: String
    ): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/purchase")
    fun purchase(
        @Body request: PurchaseRequest,
        @Header("Trigger") trigger: String,
        @Header("Attempt") attemptNumber: Int
    ): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/restore")
    fun restore(
        @Body request: RestoreRequest,
        @Header("Trigger") trigger: String
    ): Call<BaseResponse<QLaunchResult>>

    @POST("attribution")
    fun attribution(@Body request: AttributionRequest): Call<BaseResponse<Response>>

    @POST("v1/products/get")
    fun eligibility(@Body request: EligibilityRequest): Call<BaseResponse<EligibilityResult>>

    @POST("v2/identities")
    fun identify(@Body request: IdentityRequest): Call<Data<IdentityResult>>

    @Headers("Content-Type: application/json")
    @POST
    fun crashLogs(@Body request: CrashRequest, @Url url: String = CRASH_LOGS_URL): Call<Void>

    @GET("v3/remote-config")
    fun remoteConfig(
        @Query("user_id") userId: String,
        @Query("context_key") contextKey: String?
    ): Call<QRemoteConfig>

    @GET("v3/remote-configs")
    fun remoteConfigList(
        @Query("user_id") userId: String,
        @Query("all_context_keys") allContextKeys: Boolean = true,
    ): Call<List<QRemoteConfig>>

    @GET("v3/remote-configs")
    fun remoteConfigList(
        @Query("user_id") userId: String,
        @Query("context_key") contextKeys: List<String>,
        @Query("with_empty_context_key") includeEmptyContextKey: Boolean,
    ): Call<List<QRemoteConfig>>

    @POST("v3/experiments/{id}/users/{user_id}")
    fun attachUserToExperiment(
        @Path("id") experimentId: String,
        @Path("user_id") userId: String,
        @Body request: AttachUserRequest
    ): Call<Void>

    @DELETE("v3/experiments/{id}/users/{user_id}")
    fun detachUserFromExperiment(
        @Path("id") experimentId: String,
        @Path("user_id") userId: String
    ): Call<Void>

    @POST("v3/remote-configurations/{id}/users/{user_id}")
    fun attachUserToRemoteConfiguration(
        @Path("id") experimentId: String,
        @Path("user_id") userId: String
    ): Call<Void>

    @DELETE("v3/remote-configurations/{id}/users/{user_id}")
    fun detachUserFromRemoteConfiguration(
        @Path("id") experimentId: String,
        @Path("user_id") userId: String
    ): Call<Void>

    @POST("v3/users/{user_id}/properties")
    fun sendProperties(
        @Path("user_id") userId: String,
        @Body properties: List<UserPropertyRequestData>
    ): Call<SendPropertiesResult>

    @GET("v3/users/{user_id}/properties")
    fun getProperties(@Path("user_id") userId: String): Call<List<QUserProperty>>

    // --- Web2App redemption (DEV-847) ---

    /**
     * Exchanges a redemption token (delivered to the user via App Link email)
     * for an entitlement on the current SDK user. See plan §"Mobile SDK API
     * surface → Android" and `RedemptionResult` for the response semantics.
     *
     * [idempotencyKey] is a UUID scoped to one *logical* redeem (parity with
     * iOS): the same value is sent on the redeem POST and any 409→/status
     * recovery call so the backend can dedup double-taps / retries — including
     * across a cold-start (the key is persisted per token).
     */
    @POST("v4/web/redeem")
    fun redeem(
        @Body request: RedeemRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Call<RedeemResponse>

    /**
     * Disambiguates a 409 from [redeem]: tells the SDK whether the token was
     * already consumed (→ `AlreadyConsumed`) versus some other transient
     * server condition. Used to surface the identify-flow recovery hint to
     * the host app (RT4-W2). Carries the same [idempotencyKey] as the redeem
     * POST it recovers from (same logical redeem).
     */
    @POST("v4/web/redeem/status")
    fun redeemStatus(
        @Body request: RedeemStatusRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Call<RedeemStatusResponse>

    /**
     * Requests a new redemption email when the original token is lost or
     * expired. Backend always responds 200 on a well-formed email (no oracle
     * leak); 429 / 503 are surfaced to the UI for retry messaging.
     *
     * [idempotencyKey] is a fresh UUID per reissue (its own logical operation).
     */
    @POST("v4/web/redeem/reissue")
    fun redeemReissue(
        @Body request: RedeemReissueRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Call<Void>
}
