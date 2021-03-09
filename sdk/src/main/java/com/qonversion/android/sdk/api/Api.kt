package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.automations.Screen
import com.qonversion.android.sdk.dto.eligibility.EligibilityResult
import com.qonversion.android.sdk.dto.request.*
import retrofit2.Call
import retrofit2.http.*

interface Api {

    @POST("v1/user/init")
    fun init(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: InitRequest
    ): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/purchase")
    fun purchase(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: PurchaseRequest
    ): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/restore")
    fun restore(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: RestoreRequest
    ): Call<BaseResponse<QLaunchResult>>

    @POST("attribution")
    fun attribution(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: AttributionRequest
    ): Call<BaseResponse<Response>>

    @POST("v1/properties")
    fun properties(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: PropertiesRequest
    ): Call<BaseResponse<Response>>

    @POST("v1/products/get")
    fun eligibility(
        @HeaderMap headers: ApiHeaders.Default,
        @Body request: EligibilityRequest
    ): Call<BaseResponse<EligibilityResult>>

    @GET("v2/screens/{id}")
    fun screens(
        @HeaderMap headers: ApiHeaders.Screens,
        @Path("id") screenId: String
    ): Call<Data<Screen>>

    @POST("/v2/screens/{id}/views")
    fun views(
        @HeaderMap headers: ApiHeaders.Default,
        @Path("id") screenId: String,
        @Body request: ViewsRequest
    ): Call<Void>

    @GET("v2/users/{id}/action-points")
    fun actionPoints(
        @HeaderMap headers: ApiHeaders.Default,
        @Path("id") userId: String,
        @QueryMap params: Map<String, String>
    ): Call<Data<ActionPoints>>

}