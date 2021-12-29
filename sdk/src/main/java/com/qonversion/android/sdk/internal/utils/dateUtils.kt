package com.qonversion.android.sdk.internal.utils

import java.text.SimpleDateFormat
import java.util.*

internal const val DAYS_IN_MONTH = 30L
internal const val DAYS_IN_WEEK = 7L
internal const val MIN_IN_HOUR = 60L
internal const val SEC_IN_MIN = 60L
internal const val HOURS_IN_DAY = 24L
internal const val SEC_IN_DAY = HOURS_IN_DAY * MIN_IN_HOUR * SEC_IN_MIN
internal const val MS_IN_SEC = 1000L

internal fun Long.msToSec(): Long = this / MS_IN_SEC

internal fun Long.toTimeString(format: String = "yyyy.MM.dd HH:mm"): String {
    val date = Date(this)
    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
    return dateFormat.format(date)
}
