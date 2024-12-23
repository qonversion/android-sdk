package io.qonversion.nocodes.internal.networkLayer.requestConfigurator

import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.headerBuilder.HeaderBuilder
import io.qonversion.nocodes.internal.provider.PrimaryConfigProvider

internal class RequestConfiguratorImpl(
    private val headerBuilder: HeaderBuilder,
    private val baseUrl: String,
    private val primaryConfigProvider: PrimaryConfigProvider,
) : RequestConfigurator {

    override fun configureScreenRequest(screenId: String): Request {
        val headers = headerBuilder.buildCommonHeaders()

        return Request.get("$baseUrl/${ApiEndpoint.Screen.path}/$screenId", headers)
    }
}
