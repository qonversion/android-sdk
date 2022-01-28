package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder

internal class RequestConfiguratorImpl(
    private val headerBuilder: HeaderBuilder,
    private val baseUrl: String
) : RequestConfigurator {

    override fun configureUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("$baseUrl/${ApiEndpoint.Users.path}/$id", headers)
    }

    override fun configureCreateUserRequest(id: String): Request {
        val headers = headerBuilder.buildCommonHeaders()
        val body = mapOf("id" to id)

        return Request.post("$baseUrl/${ApiEndpoint.Users.path}", headers, body)
    }

    override fun configureUserPropertiesRequest(properties: Map<String, String>): Request {
        val headers = headerBuilder.buildCommonHeaders()
        // TODO delete access_token and q_uid from the body after migrating API to v2
        val body = mapOf(
            "access_token" to InternalConfig.primaryConfig.projectKey,
            "q_uid" to InternalConfig.uid,
            "properties" to properties
        )

        return Request.post("$baseUrl/${ApiEndpoint.Properties.path}", headers, body)
    }
}
