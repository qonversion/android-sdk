package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.automations.Screen
import com.qonversion.android.sdk.dto.eligibility.EligibilityResult
import com.qonversion.android.sdk.dto.identity.IdentityResult
import com.qonversion.android.sdk.dto.request.*
import retrofit2.Call
import retrofit2.http.*

private const val PARAM_ID = "id"
private const val IDENTITIES_URL = "v3/identities/{$PARAM_ID}"

interface Api {

    @POST("v1/user/init")
    fun init(@Body request: InitRequest): Call<BaseResponse<QLaunchResult>>

    @POST("v3/users/{$PARAM_ID}/purchases")
    fun purchase(@Path(PARAM_ID) userId: String, @Body request: PurchaseRequest): Call<UserPurchase>

    @GET("v3/users/{$PARAM_ID}/entitlements")
    fun entitlements(@Path(PARAM_ID) userId: String): Call<Data<List<QEntitlement>>>

    @POST("v1/user/restore")
    fun restore(@Body request: RestoreRequest): Call<BaseResponse<QRestoreResult>>

    @POST("attribution")
    fun attribution(@Body request: AttributionRequest): Call<BaseResponse<Response>>

    @POST("v1/properties")
    fun properties(@Body request: PropertiesRequest): Call<BaseResponse<Response>>

    @POST("v1/products/get")
    fun eligibility(@Body request: EligibilityRequest): Call<BaseResponse<EligibilityResult>>

    @POST(IDENTITIES_URL)
    fun createIdentity(@Path(PARAM_ID) userID: String, @Body request: CreateIdentityRequest): Call<IdentityResult>

    @GET(IDENTITIES_URL)
    fun obtainIdentity(@Path(PARAM_ID) userID: String): Call<IdentityResult>

    @GET("v2/screens/{id}")
    fun screens(@Path("id") screenId: String): Call<Data<Screen>>

    @POST("/v2/screens/{id}/views")
    fun views(@Path("id") screenId: String, @Body request: ViewsRequest): Call<Void>

    @GET("v2/users/{id}/action-points")
    fun actionPoints(
        @Path("id") userId: String,
        @QueryMap params: Map<String, String>
    ): Call<Data<ActionPoints>>

    @POST("v2/events")
    fun events(@Body request: EventRequest): Call<Void>
}
