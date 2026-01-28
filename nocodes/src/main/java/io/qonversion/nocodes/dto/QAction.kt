package io.qonversion.nocodes.dto

import io.qonversion.nocodes.error.NoCodesError

data class QAction(
    val type: Type,
    val parameters: Map<Parameter, Any>? = null,
    val successAction: QSuccessFailureAction? = null,
    val failureAction: QSuccessFailureAction? = null
) {
    var error: NoCodesError? = null

    constructor(type: Type, parameter: Parameter, value: Any) : this(type, mapOf(parameter to value), null, null)

    enum class Type(val type: String) {
        Unknown("unknown"),
        Url("url"),
        DeepLink("deeplink"),
        Navigation("navigation"),
        Purchase("purchase"),
        Restore("restore"),
        Close("close"),
        CloseAll("closeAll"),
        LoadProducts("getProducts"),
        ShowScreen("showScreen"),
        GoToPage("goToPage");

        companion object {
            fun from(type: String?): Type {
                if (type == "makePurchase") {
                    return Purchase
                }
                return entries.find { it.type == type } ?: Unknown
            }
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

/**
 * Type of success/failure action
 */
enum class QSuccessFailureActionType(val type: String) {
    None("none"),
    Close("close"),
    CloseAll("closeAll"),
    Navigation("navigation"),
    Url("url"),
    DeepLink("deeplink"),
    GoToPage("goToPage");

    companion object {
        fun from(type: String?): QSuccessFailureActionType? {
            return entries.find { it.type == type }
        }
    }
}

/**
 * Represents a success or failure action with its optional value
 */
data class QSuccessFailureAction(
    val type: QSuccessFailureActionType,
    val value: String? = null
)
