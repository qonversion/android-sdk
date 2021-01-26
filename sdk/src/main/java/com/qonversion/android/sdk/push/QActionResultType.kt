package com.qonversion.android.sdk.push

enum class QActionResultType(val type: String) {
    Unknown("unknown"),
    Url("url"),
    DeepLink("deeplink"),
    Navigation("navigate"),
    Purchase("purchase"),
    Restore("restore"),
    Close("close");

    companion object {
        fun fromType(type: String?): QActionResultType {
            return when (type) {
                "url" -> Url
                "deeplink" -> DeepLink
                "navigate" -> Navigation
                "purchase" -> Purchase
                "restore" -> Restore
                "close" -> Close
                else -> Unknown
            }
        }
    }
}
