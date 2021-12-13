package com.qonversion.android.sdk.internal.networkLayer.dto

import com.qonversion.android.sdk.internal.networkLayer.utils.isInternalServerErrorCode
import com.qonversion.android.sdk.internal.networkLayer.utils.isSuccessHttpCode

internal class RawResponse(
    val code: Int,
    val payload: Any // Array or Map or String (if internal server error occurred)
) {
    val isSuccess: Boolean = code.isSuccessHttpCode
    val isInternalServerError: Boolean = code.isInternalServerErrorCode
}
