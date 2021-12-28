package com.qonversion.android.sdk.internal.serializers.mappers.error

import com.qonversion.android.sdk.internal.networkLayer.dto.Response

internal interface ErrorResponseMapper {

    fun fromMap(data: Map<*, *>, code: Int): Response.Error
}
