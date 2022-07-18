package com.qonversion.android.sdk.dto

enum class QEntitlementCacheLifetime(val days: Int) {
    WEEK(7),
    TWO_WEEKS(14),
    MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    YEAR(360),
    PERMANENT(Int.MAX_VALUE)
}
