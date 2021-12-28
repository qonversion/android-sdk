package com.qonversion.android.sdk.internal.serializers.mappers.error

import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.serializers.mappers.getString

internal class ApiErrorMapper : ErrorResponseMapper {

    override fun fromMap(data: Map<*, *>, code: Int): Response.Error {
        val message = data.getString("message") ?: "No message provided"
        val type = data.getString("type")
        val apiCode = data.getString("code")

        return Response.Error(code, message, type, apiCode)
    }
}
