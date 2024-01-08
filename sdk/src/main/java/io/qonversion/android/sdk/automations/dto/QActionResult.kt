package io.qonversion.android.sdk.automations.dto

import io.qonversion.android.sdk.dto.QonversionError

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
) {
    var error: QonversionError? = null
}
