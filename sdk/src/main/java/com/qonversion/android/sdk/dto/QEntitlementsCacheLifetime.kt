package com.qonversion.android.sdk.dto

enum class QEntitlementsCacheLifetime(val days: Int) {
    WEEK(7),
    TWO_WEEKS(14),
    MONTH(30),
    TWO_MONTHS(60),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    YEAR(365),
    UNLIMITED(Int.MAX_VALUE)
}
