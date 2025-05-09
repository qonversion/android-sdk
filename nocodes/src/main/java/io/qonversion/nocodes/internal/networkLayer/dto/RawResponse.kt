package io.qonversion.nocodes.internal.networkLayer.dto

import io.qonversion.nocodes.internal.networkLayer.utils.isInternalServerErrorHttpCode
import io.qonversion.nocodes.internal.networkLayer.utils.isSuccessHttpCode

internal class RawResponse(
    val code: Int,
    val payload: Any // Array or Map
) {
    val isSuccess: Boolean = code.isSuccessHttpCode
    val isInternalServerError: Boolean = code.isInternalServerErrorHttpCode
}
