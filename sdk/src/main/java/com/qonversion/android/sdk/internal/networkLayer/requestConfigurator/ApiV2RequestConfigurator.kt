package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder

internal class ApiV2RequestConfigurator(
    private val headerBuilder: HeaderBuilder
): RequestConfigurator {

    override fun configureUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()
        return Request.get("$BASE_URL/users/$id", headers)
    }

    override fun configureCreateUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()
        val body = mapOf("id" to id)
        return Request.post("$BASE_URL/users", headers, body)
    }

    companion object {
        const val BASE_URL = "https://api.qonversion.io/v2"
    }
}
