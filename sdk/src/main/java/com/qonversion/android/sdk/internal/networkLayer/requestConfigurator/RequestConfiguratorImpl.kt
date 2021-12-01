package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder

internal class RequestConfiguratorImpl(
    private val headerBuilder: HeaderBuilder,
    private val baseUrl: String
): RequestConfigurator {

    override fun configureUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("$baseUrl/${ApiEndpoint.Users.path}/$id", headers)
    }

    override fun configureCreateUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()
        val body = mapOf("id" to id)

        return Request.post("$baseUrl/${ApiEndpoint.Users.path}", headers, body)
    }
}
