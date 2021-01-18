package com.qonversion.android.sdk.dto.products

enum class QProductDuration(val type: Int) {
    Weekly(0),
    Monthly(1),
    ThreeMonthly(2),
    SixMonthly(3),
    Annual(4);

    companion object {
        fun fromType(type: Int): QProductDuration? {
            return when (type) {
                0 -> Weekly
                1-> Monthly
                2-> ThreeMonthly
                3-> SixMonthly
                4-> Annual
                else -> null
            }
        }
    }
}
