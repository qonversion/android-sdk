package com.qonversion.android.sdk.internal.dto.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class CrashRequest(
    @Json(name = "exception") val log: ExceptionInfo,
    @Json(name = "device") val deviceInfo: DeviceInfo
) {
    @JsonClass(generateAdapter = true)
    data class ExceptionInfo(
        @Json(name = "title") val title: String,
        @Json(name = "place") val place: String,
        @Json(name = "traces") val traces: List<ExceptionTrace>
    )

    @JsonClass(generateAdapter = true)
    data class ExceptionTrace(
        @Json(name = "rawStackTrace") val rawStackTrace: String,
        @Json(name = "class") val className: String,
        @Json(name = "message") val message: String,
        @Json(name = "elements") val elements: List<ExceptionTraceElement>
    )

    @JsonClass(generateAdapter = true)
    data class ExceptionTraceElement(
        @Json(name = "class") val className: String,
        @Json(name = "file") val fileName: String,
        @Json(name = "method") val methodName: String,
        @Json(name = "line") val line: Int
    )

    @JsonClass(generateAdapter = true)
    data class DeviceInfo(
        @Json(name = "platform") val platform: String,
        @Json(name = "platform_version") val platformVersion: String,
        @Json(name = "source") val source: String,
        @Json(name = "source_version") val sourceVersion: String,
        @Json(name = "project_key") val projectKey: String,
        @Json(name = "uid") val uid: String
    )
}
