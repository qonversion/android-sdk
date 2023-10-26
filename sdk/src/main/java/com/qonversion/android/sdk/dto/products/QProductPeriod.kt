package com.qonversion.android.sdk.dto.products

data class QProductPeriod(
    val count: Int,
    val unit: Unit,
    val iso: String
) {
    companion object {
        fun from(isoPeriod: String): QProductPeriod {
            val regex = "^P(?!\$)(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?\$".toRegex()
            val parts = regex.matchEntire(isoPeriod)
                ?: return QProductPeriod(0, Unit.Unknown, isoPeriod)

            val (sYear, sMonth, sWeek, sDay) = parts.destructured
            val year = sYear.toIntOrNull() ?: 0
            val month = sMonth.toIntOrNull() ?: 0
            val week = sWeek.toIntOrNull() ?: 0
            val day = sDay.toIntOrNull() ?: 0

            return when {
                year > 0 -> QProductPeriod(year, Unit.Year, isoPeriod)
                month > 0 -> QProductPeriod(month, Unit.Month, isoPeriod)
                week > 0 -> QProductPeriod(week, Unit.Week, isoPeriod)
                day > 0 -> QProductPeriod(day, Unit.Day, isoPeriod)
                else -> QProductPeriod(0, Unit.Unknown, isoPeriod)
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
