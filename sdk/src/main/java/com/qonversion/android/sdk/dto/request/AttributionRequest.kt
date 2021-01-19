package com.qonversion.android.sdk.dto.request

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.ProviderData
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttributionRequest(
    @Json(name = "d") val d: Environment,
    @Json(name = "v") val v: String,
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "provider_data") val providerData: ProviderData,
    @Json(name = "client_uid") var clientUid: String?
) : QonversionRequest {
    override fun authorize(clientUid: String) {
        this.clientUid = clientUid
    }

    override fun isAuthorized(): Boolean {
        if (this.clientUid == null) {
            return false
        }
        if (this.clientUid!!.isEmpty()) {
            return false
        }
        return true
    }
}