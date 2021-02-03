package com.qonversion.android.sdk.automations

enum class QActionResultType(val type: String) {
    Unknown("unknown"),
    Url("url"),
    DeepLink("deeplink"),
    Navigation("navigate"),
    Purchase("purchase"),
    Restore("restore"),
    Close("close");

    companion object {
        fun fromType(type: String?) = values().find { it.type == type } ?: Unknown
    }
}
