package com.qonversion.android.sdk.api

import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.eligibility.EligibilityResult
import com.qonversion.android.sdk.dto.request.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

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

    @POST("v1/products/get")
    fun eligibility(@Body request: EligibilityRequest): Call<BaseResponse<EligibilityResult>>

}