package com.qonversion.android.sdk.internal.dto.request

import com.qonversion.android.sdk.internal.dto.Environment
import com.qonversion.android.sdk.internal.dto.eligibility.StoreProductInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class EligibilityRequest(
    @Json(name = "install_date") override val installDate: Long,
    @Json(name = "device") override val device: Environment,
    @Json(name = "version") override val version: String,
    @Json(name = "access_token") override val accessToken: String,
    @Json(name = "q_uid") override val clientUid: String?,
    @Json(name = "receipt") override val receipt: String = "",
    @Json(name = "debug_mode") override val debugMode: String,
    @Json(name = "products_local_data") val productInfos: List<StoreProductInfo>
) : RequestData()
