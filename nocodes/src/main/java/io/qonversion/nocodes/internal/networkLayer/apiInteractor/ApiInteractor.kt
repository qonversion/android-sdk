package io.qonversion.nocodes.internal.networkLayer.apiInteractor

import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.networkLayer.RetryPolicy
import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.dto.Response

internal interface ApiInteractor {
    @Throws(NoCodesException::class)
    suspend fun execute(request: Request): Response

    @Throws(NoCodesException::class)
    suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response
}
