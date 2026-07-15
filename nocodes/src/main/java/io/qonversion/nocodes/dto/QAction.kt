package io.qonversion.nocodes.dto

import io.qonversion.nocodes.error.NoCodesError

data class QAction(
    val type: Type,
    val parameters: Map<Parameter, Any>? = null,
    val rawParameters: Map<String, Any>? = null
) {
    var error: NoCodesError? = null

    constructor(type: Type, parameter: Parameter, value: Any) : this(type, mapOf(parameter to value))

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
        GetContext("getContext"),
        ShowScreen("showScreen"),
        ScreenAnalytics("screenAnalytics"),

        /**
         * Internal action: the web page announces it renders its own purchase
         * loader, so the native purchase spinner is suppressed (no double loader).
         */
        PurchaseLoaderPresent("purchaseLoaderPresent"),

        /**
         * Custom action configured in the builder. The SDK does not execute anything
         * itself — the configured string value is delivered to the app code via
         * [io.qonversion.nocodes.interfaces.NoCodesDelegate.onCustomAction].
         */
        Custom("custom");

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
        ScreenId("screenId"),
        AnalyticsType("type"),
        PageIndex("page_index"),
        Value("value");

        companion object {
            fun from(key: String?) = entries.find { it.key == key }
        }
    }
}
