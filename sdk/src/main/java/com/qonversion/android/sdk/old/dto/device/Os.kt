package com.qonversion.android.sdk.old.dto.device

import android.os.Build
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Os(
    @Json(name = "name") val name: String = "Android",
    @Json(name = "version") val version: String = Build.VERSION.SDK_INT.toString()
)
