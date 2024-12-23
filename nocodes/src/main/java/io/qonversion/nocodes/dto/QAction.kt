package io.qonversion.nocodes.dto

import com.qonversion.android.sdk.dto.QonversionError

data class QAction(
    val type: Type,
    val parameters: Map<Parameter, String>? = null
) {
    var error: QonversionError? = null // todo

    constructor(type: Type, parameter: Parameter, value: String) : this(type, mapOf(parameter to value))

    enum class Type(val type: String) {
        Unknown("unknown"),
        Url("url"),
        DeepLink("deeplink"),
        Navigation("navigate"),
        Purchase("purchase"),
        Restore("restore"),
        Close("close"),
        CloseAll("closeAllQScreens");

        companion object {
            fun fromType(type: String?) = values().find { it.type == type } ?: Unknown
        }
    }

    enum class Parameter(val key: String) {
        Url("url"),
        ProductId("productId"),
        ScreenId("screenId")
    }
}
