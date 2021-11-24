package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response

interface NetworkClientDecorator : NetworkClient {
    suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response
}
