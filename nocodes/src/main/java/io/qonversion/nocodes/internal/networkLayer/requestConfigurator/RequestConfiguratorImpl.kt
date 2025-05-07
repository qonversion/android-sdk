package io.qonversion.nocodes.internal.networkLayer.requestConfigurator

import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilder

internal class RequestConfiguratorImpl(
    private val headerBuilder: HeaderBuilder,
    private val baseUrl: String,
) : RequestConfigurator {

    override fun configureScreenRequest(contextKey: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("$baseUrl/${ApiEndpoint.Screen.path}?context_key=$contextKey", headers)
    }

    override fun configureScreenRequestById(screenId: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("$baseUrl/${ApiEndpoint.Screen.path}?screen_id=$screenId", headers)
    }
}
