package io.qonversion.nocodes.internal.common.mappers.error

import io.qonversion.nocodes.internal.networkLayer.dto.Response

internal interface ErrorResponseMapper {

    fun fromMap(data: Map<*, *>, code: Int): Response.Error
}