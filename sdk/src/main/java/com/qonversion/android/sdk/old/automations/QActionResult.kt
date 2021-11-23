package com.qonversion.android.sdk.old.automations

import com.qonversion.android.sdk.old.QonversionError

data class QActionResult(
    val type: QActionResultType,
    val value: Map<String, String>? = null
) {
    var error: QonversionError? = null
}
