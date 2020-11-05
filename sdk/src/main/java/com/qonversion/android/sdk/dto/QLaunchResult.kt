package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

//@JsonClass(generateAdapter = true)
//data class QLaunchNewResult(
//    @Json(name = "uid") val uid: String,
////    @Json(name = "timestamp") val timestamp: Timestamp,
//    val permissions: Map<String, QPermission>? = null,
//    val products: Map<String, QProduct>? = null,
//    val userProducts: Map<String, QProduct>? = null
//)

@JsonClass(generateAdapter = true)
data class QLaunchResult(
    @Json(name = "uid") val uid: String,
    @Json(name = "timestamp") val date: Date,
    @Json(name = "products") val products: Map<String, QProduct> = mapOf(),
    @Json(name = "permissions") val permissions: Map<String, QPermission> = mapOf(),
    @Json(name = "user_products") val userProducts: Map<String, QProduct> = mapOf()
)