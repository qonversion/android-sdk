package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class QFallbackObject(
    @Json(name = "products") val products: Map<String, QProduct> = mapOf(),
    @Json(name = "offerings") val offerings: QOfferings?,
    @Json(name = "products_permissions") val productPermissions: Map<String, List<String>>?,
    @Json(name = "remote_config_list") val remoteConfigList: QRemoteConfigList?,
)
