package io.qonversion.nocodes.internal.networkLayer.requestConfigurator

import io.qonversion.nocodes.internal.networkLayer.dto.Request

internal interface RequestConfigurator {

    fun configureScreenRequest(screenId: String): Request
}