package com.qonversion.android.sdk.dto.products

enum class QTrialDuration(val type: Int) {
    NotAvailable(-1),
    ThreeDays(1),
    Week(2),
    TwoWeeks(3),
    Month(4),
    TwoMonths(5),
    ThreeMonths(6),
    SixMonths(7),
    Year(8),
    Other(9);

    companion object {
        fun fromType(type: Int): QTrialDuration {
            return when (type) {
                -1 -> NotAvailable
                1 -> ThreeDays
                2 -> Week
                3 -> TwoWeeks
                4 -> Month
                5 -> TwoMonths
                6 -> ThreeMonths
                7 -> SixMonths
                8 -> Year
                9 -> Other
                else -> Other
            }
        }
    }
}
