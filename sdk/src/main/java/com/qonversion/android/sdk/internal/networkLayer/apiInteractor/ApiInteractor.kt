package com.qonversion.android.sdk.internal.networkLayer.apiInteractor

import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response

internal interface ApiInteractor {
    @Throws(QonversionException::class)
    suspend fun execute(request: Request): Response

    @Throws(QonversionException::class)
    suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response
}
