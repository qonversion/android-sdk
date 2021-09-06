package com.qonversion.android.sdk.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseResponse<T>(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: T
)

inline fun <T, R> BaseResponse<T>.getOrThrow(func: (BaseResponse<T>) -> R): R =
    if (success) {
        func(this)
    } else {
        throw RuntimeException("backend exception")
    }
