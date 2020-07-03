package com.qonversion.android.sdk.api


import com.qonversion.android.sdk.dto.*
import io.reactivex.Single
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface Api {

    @POST("init")
    fun init(@Body request: InitRequest): Call<BaseResponse<Response>>

    @POST("purchase")
    fun purchase(@Body request: PurchaseRequest): Single<BaseResponse<Response>>

    @POST("attribution")
    fun attribution(@Body request: AttributionRequest): Call<BaseResponse<Response>>

}