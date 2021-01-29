package com.qonversion.android.sdk.push

import com.qonversion.android.sdk.QonversionError

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
){
    var error: QonversionError? = null
}