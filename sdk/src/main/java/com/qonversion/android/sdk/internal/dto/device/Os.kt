package com.qonversion.android.sdk.internal.dto.device

import android.os.Build
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Os(
    @Json(name = "name") val name: String = "Android",
    @Json(name = "version") val version: String = Build.VERSION.SDK_INT.toString()
)
