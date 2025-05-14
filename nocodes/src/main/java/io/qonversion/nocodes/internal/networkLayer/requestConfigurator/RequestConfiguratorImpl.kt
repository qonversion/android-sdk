package io.qonversion.nocodes.internal.networkLayer.requestConfigurator

import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilder

internal class RequestConfiguratorImpl(
    private val headerBuilder: HeaderBuilder,
    private val baseUrl: String,
) : RequestConfigurator {

    override fun configureScreenRequest(contextKey: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("${baseUrl}v3/${ApiEndpoint.Contexts.path}/$contextKey/${ApiEndpoint.Screens.path}", headers)
    }

    override fun configureScreenRequestById(screenId: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("${baseUrl}v3/${ApiEndpoint.Screens.path}/$screenId", headers)
    }
}
