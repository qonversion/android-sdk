package com.qonversion.android.sdk.internal.mappers

import com.qonversion.android.sdk.internal.networkLayer.dto.Response

internal interface ErrorResponseMapper {

    fun fromMap(data: Map<*, *>, code: Int): Response.Error
}
