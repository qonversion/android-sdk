package com.qonversion.android.sdk.push

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
)