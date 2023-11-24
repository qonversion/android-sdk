package com.qonversion.android.sdk.dto.products

/**
 * A class describing a subscription period
 */
data class QSubscriptionPeriod(
    /**
     * A count of subsequent intervals.
     */
    val unitCount: Int,

    /**
     * Interval unit.
     */
    val unit: Unit,

    /**
     * ISO 8601 representation of the period.
     */
    val iso: String
) {
    companion object {
        fun from(isoPeriod: String): QSubscriptionPeriod {
            fun String.toPeriodCount() = takeIf { isNotEmpty() }
                ?.substring(0, length - 1)
                ?.toIntOrNull() ?: 0

            val regex = "^P(?!\$)(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?\$".toRegex()
            val parts = regex.matchEntire(isoPeriod)
                ?: return QSubscriptionPeriod(0, Unit.Unknown, isoPeriod)

            val (sYear, sMonth, sWeek, sDay) = parts.destructured
            val year = sYear.toPeriodCount()
            val month = sMonth.toPeriodCount()
            val week = sWeek.toPeriodCount()
            val day = sDay.toPeriodCount()

            return when {
                year > 0 -> QSubscriptionPeriod(year, Unit.Year, isoPeriod)
                month > 0 -> QSubscriptionPeriod(month, Unit.Month, isoPeriod)
                week > 0 -> QSubscriptionPeriod(week, Unit.Week, isoPeriod)
                day > 0 -> QSubscriptionPeriod(day, Unit.Day, isoPeriod)
                else -> QSubscriptionPeriod(0, Unit.Unknown, isoPeriod)
            }
        }
    }

    enum class Unit {
        Day,
        Week,
        Month,
        Year,
        Unknown,
    }
}
