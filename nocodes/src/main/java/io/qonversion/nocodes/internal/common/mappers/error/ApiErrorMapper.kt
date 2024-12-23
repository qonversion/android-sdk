package io.qonversion.nocodes.internal.common.mappers.error

import io.qonversion.nocodes.internal.common.mappers.getString
import io.qonversion.nocodes.internal.networkLayer.dto.Response

internal class ApiErrorMapper : ErrorResponseMapper {

    override fun fromMap(data: Map<*, *>, code: Int): Response.Error {
        val message = data.getString("message") ?: "No message provided"
        val type = data.getString("type")
        val apiCode = data.getString("code")

        return Response.Error(code, message, type, apiCode)
    }
}