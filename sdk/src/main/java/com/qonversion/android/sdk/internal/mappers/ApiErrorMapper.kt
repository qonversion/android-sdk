package com.qonversion.android.sdk.internal.mappers

import com.qonversion.android.sdk.internal.networkLayer.dto.Response

internal class ApiErrorMapper: ErrorResponseMapper {

    override fun fromMap(data: Map<*, *>, code: Int): Response.Error {
        val type = data.getString("type")
        val apiCode = data.getString("code")
        val message = data.getString("message")

        return Response.Error(code, type, apiCode, message)
    }
}