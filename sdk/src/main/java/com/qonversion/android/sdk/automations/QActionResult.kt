package com.qonversion.android.sdk.automations

import com.qonversion.android.sdk.QonversionError

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
) {
    var error: QonversionError? = null
}
