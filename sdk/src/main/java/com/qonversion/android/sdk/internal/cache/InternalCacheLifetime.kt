package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.internal.utils.SEC_IN_MIN

internal enum class InternalCacheLifetime(val seconds: Long) {

    FIVE_MIN(5 * SEC_IN_MIN),
    ONE_DAY(CacheLifetime.ONE_DAY.seconds),
    TWO_DAYS(CacheLifetime.TWO_DAYS.seconds),
    THREE_DAYS(CacheLifetime.THREE_DAYS.seconds),
    WEEK(CacheLifetime.WEEK.seconds),
    TWO_WEEKS(CacheLifetime.TWO_WEEKS.seconds),
    MONTH(CacheLifetime.MONTH.seconds);

    fun from(cacheLifetime: CacheLifetime): InternalCacheLifetime = when (cacheLifetime) {
        CacheLifetime.ONE_DAY -> ONE_DAY
        CacheLifetime.TWO_DAYS -> TWO_DAYS
        CacheLifetime.THREE_DAYS -> THREE_DAYS
        CacheLifetime.WEEK -> WEEK
        CacheLifetime.TWO_WEEKS -> TWO_WEEKS
        CacheLifetime.MONTH -> MONTH
    }
}
