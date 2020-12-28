package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.automation.Screen
import com.qonversion.android.sdk.dto.automation.ViewsRequest
import retrofit2.Call
import retrofit2.http.*

interface Api {

    @POST("v1/user/init")
    fun init(@Body request: InitRequest): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/purchase")
    fun purchase(@Body request: PurchaseRequest): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/restore")
    fun restore(@Body request: RestoreRequest): Call<BaseResponse<QLaunchResult>>

    @POST("attribution")
    fun attribution(@Body request: AttributionRequest): Call<BaseResponse<Response>>

    @POST("v1/properties")
    fun properties(@Body request: PropertiesRequest): Call<BaseResponse<Response>>

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