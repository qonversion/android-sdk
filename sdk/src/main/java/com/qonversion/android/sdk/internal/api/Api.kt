package com.qonversion.android.sdk.internal.api

import com.qonversion.android.sdk.internal.Constants.CRASH_LOGS_URL
import com.qonversion.android.sdk.internal.dto.automations.Screen
import com.qonversion.android.sdk.internal.dto.eligibility.EligibilityResult
import com.qonversion.android.sdk.internal.dto.identity.IdentityResult
import com.qonversion.android.sdk.internal.dto.ActionPoints
import com.qonversion.android.sdk.internal.dto.BaseResponse
import com.qonversion.android.sdk.internal.dto.Data
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.Response
import com.qonversion.android.sdk.internal.dto.request.AttributionRequest
import com.qonversion.android.sdk.internal.dto.request.CrashRequest
import com.qonversion.android.sdk.internal.dto.request.EligibilityRequest
import com.qonversion.android.sdk.internal.dto.request.EventRequest
import com.qonversion.android.sdk.internal.dto.request.IdentityRequest
import com.qonversion.android.sdk.internal.dto.request.InitRequest
import com.qonversion.android.sdk.internal.dto.request.PropertiesRequest
import com.qonversion.android.sdk.internal.dto.request.PurchaseRequest
import com.qonversion.android.sdk.internal.dto.request.RestoreRequest
import com.qonversion.android.sdk.internal.dto.request.SendPushTokenRequest
import com.qonversion.android.sdk.internal.dto.request.ViewsRequest
import retrofit2.Call
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.QueryMap
import retrofit2.http.Url

internal interface Api {

    @POST("v1/user/init")
    fun init(@Body request: InitRequest): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/push-token")
    fun sendPushToken(@Body request: SendPushTokenRequest): Call<Void>

    @POST("v1/user/purchase")
    fun purchase(@Body request: PurchaseRequest): Call<BaseResponse<QLaunchResult>>

    @POST("v1/user/restore")
    fun restore(@Body request: RestoreRequest): Call<BaseResponse<QLaunchResult>>

    @POST("attribution")
    fun attribution(@Body request: AttributionRequest): Call<BaseResponse<Response>>

    @POST("v1/properties")
    fun properties(@Body request: PropertiesRequest): Call<BaseResponse<Response>>

    @POST("v1/products/get")
    fun eligibility(@Body request: EligibilityRequest): Call<BaseResponse<EligibilityResult>>

    @POST("v2/identities")
    fun identify(@Body request: IdentityRequest): Call<Data<IdentityResult>>

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

    @Headers("Content-Type: application/json")
    @POST
    fun crashLogs(@Body request: CrashRequest, @Url url: String = CRASH_LOGS_URL): Call<Void>
}
