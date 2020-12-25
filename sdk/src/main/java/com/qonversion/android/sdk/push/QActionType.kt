package com.qonversion.android.sdk.push

enum class QActionType(val type: String) {
    Unknown("unknown"),
    Url("url"),
    DeepLink("deeplink"),
    Navigate("navigate"),
    Purchase("purchase"),
    Restore("restore"),
    Close("close");

    companion object {
        fun fromType(type: String?): QActionType {
            return when (type) {
                "url" -> Url
                "deeplink" -> DeepLink
                "navigate" -> Navigate
                "purchase" -> Purchase
                "restore" -> Restore
                "close" -> Close
                else -> Unknown
            }
        }
    }
}
