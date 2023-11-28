package com.qonversion.android.sdk.dto.products

enum class QProductType {
    Unknown,
    Trial,
    Subscription,
    InApp;

    companion object {
        fun fromType(type: Int): QProductType {
            return when (type) {
                0 -> Trial
                1 -> Subscription
                2 -> InApp
                else -> throw IllegalArgumentException("Undefined enum type")
            }
        }
    }
}
