package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.internal.utils.SEC_IN_MIN

internal enum class InternalCacheLifetime(val seconds: Long) {
    FiveMin(5 * SEC_IN_MIN),
    OneDay(CacheLifetime.OneDay.seconds),
    TwoDays(CacheLifetime.TwoDays.seconds),
    ThreeDays(CacheLifetime.ThreeDays.seconds),
    Week(CacheLifetime.Week.seconds),
    TwoWeeks(CacheLifetime.TwoWeeks.seconds),
    Month(CacheLifetime.Month.seconds);

    fun from(cacheLifetime: CacheLifetime): InternalCacheLifetime = when (cacheLifetime) {
        CacheLifetime.OneDay -> OneDay
        CacheLifetime.TwoDays -> TwoDays
        CacheLifetime.ThreeDays -> ThreeDays
        CacheLifetime.Week -> Week
        CacheLifetime.TwoWeeks -> TwoWeeks
        CacheLifetime.Month -> Month
    }
}
