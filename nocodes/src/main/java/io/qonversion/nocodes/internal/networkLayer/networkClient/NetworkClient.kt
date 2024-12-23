package io.qonversion.nocodes.internal.networkLayer.networkClient

import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.networkLayer.dto.RawResponse
import io.qonversion.nocodes.internal.networkLayer.dto.Request

internal interface NetworkClient {
    @Throws(NoCodesException::class)
    suspend fun execute(request: Request): RawResponse
}