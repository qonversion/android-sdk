package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.dto.*
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
    ): Call<ResponseV2<QScreen>>


    @POST("/v2/screens/{id}/views")
    fun views(
        @HeaderMap headers: ApiHeaders.Default,
        @Path("id") screenId: String,
        @Body request: ViewsRequest
    ): Call<Void>

}