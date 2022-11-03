package com.qonversion.android.sdk.automations.dto

import com.qonversion.android.sdk.dto.QonversionError

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
) {
    var error: QonversionError? = null
}
