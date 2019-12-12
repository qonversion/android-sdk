package com.qonversion.android.sdk.api


import com.qonversion.android.sdk.dto.BaseResponse
import com.qonversion.android.sdk.dto.InitRequest
import com.qonversion.android.sdk.dto.PurchaseRequest
import com.qonversion.android.sdk.dto.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface Api {

@POST("init")
fun init(@Body request: InitRequest): Call<BaseResponse<Response>>

@POST("purchase")
fun purchase(@Body request: PurchaseRequest): Call<BaseResponse<Response>>

}