package com.qonversion.android.sdk.dto

enum class QEntitlementsCacheLifetime(val days: Int) {
    Week(7),
    TwoWeeks(14),
    Month(30),
    TwoMonths(60),
    ThreeMonths(90),
    SixMonths(180),
    Year(365),
    Unlimited(Int.MAX_VALUE)
}
