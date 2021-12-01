package com.qonversion.android.sdk.internal.networkLayer.dto

private const val MIN_SUCCESS_CODE = 200
private const val MAX_SUCCESS_CODE = 299

internal class Response(
    val code: Int,
    val payload: Any // Array or Map
) {
    val isSuccess: Boolean = code in MIN_SUCCESS_CODE..MAX_SUCCESS_CODE
}
