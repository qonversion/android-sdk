package io.qonversion.nocodes.dto

import io.qonversion.nocodes.error.NoCodesError

data class QAction(
    val type: Type,
    val parameters: Map<Parameter, String>? = null
) {
    var error: NoCodesError? = null

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
            fun fromType(type: String?) = entries.find { it.type == type } ?: Unknown
        }
    }

    enum class Parameter(val key: String) {
        Url("url"),
        ProductId("productId"),
        ScreenId("screenId")
    }
}
