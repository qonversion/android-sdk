package com.qonversion.android.sdk.dto

enum class QProductDuration(val type: Int) {
    Weekly(1),
    Monthly(2),
    ThreeMonthly(3),
    SixMonthly(4),
    Annual(5);

    companion object {
        fun fromType(type: Int): QProductDuration? {
            return when (type) {
                1 -> Weekly
                2-> Monthly
                3-> ThreeMonthly
                4-> SixMonthly
                5-> Annual
                else -> null
            }
        }
    }
}
