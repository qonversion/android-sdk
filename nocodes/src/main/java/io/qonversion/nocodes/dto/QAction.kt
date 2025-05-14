package io.qonversion.nocodes.dto

import io.qonversion.nocodes.error.NoCodesError

data class QAction(
    val type: Type,
    val parameters: Map<Parameter, Any>? = null
) {
    var error: NoCodesError? = null

    constructor(type: Type, parameter: Parameter, value: Any) : this(type, mapOf(parameter to value))

    enum class Type(val type: String) {
        Unknown("unknown"),
        Url("url"),
        DeepLink("deeplink"),
        Navigation("navigation"),
        Purchase("makePurchase"),
        Restore("restore"),
        Close("close"),
        CloseAll("closeAll"),
        LoadProducts("getProducts"),
        ShowScreen("showScreen");

        companion object {
            fun from(type: String?) = entries.find { it.type == type } ?: Unknown
        }
    }

    enum class Parameter(val key: String) {
        Url("url"),
        Deeplink("deeplink"),
        ProductId("productId"),
        ProductIds("productIds"),
        ScreenId("screenId");

        companion object {
            fun from(key: String?) = entries.find { it.key == key }
        }
    }
}
