package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.internal.utils.DAYS_IN_MONTH
import com.qonversion.android.sdk.internal.utils.DAYS_IN_WEEK
import com.qonversion.android.sdk.internal.utils.SEC_IN_DAY

/**
 * The Qonversion SDK caches some information from the billing library or API.
 * This enum contains different available settings for cache lifetime.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK
 * or via [Qonversion.setCacheLifetime] after initializing the SDK.
 * The provided value is used for background requests (when the app is
 * in the background) or for case when user internet connection is not stable.
 * Cache lifetime for foreground requests is much less than
 * for background ones and is not configurable. Let's say we have user info
 * loaded and cached a day before yesterday. If the cache lifetime is set
 * to [CacheLifetime.ThreeDays] and you request user info when the app is in
 * the background (or internet connection is not stable) then the cached value will be returned.
 * But if you request it from the foreground app
 * or the cache lifetime is set to [CacheLifetime.OneDay],
 * then cached data will be renewed and then returned.
 *
 * The default value is [CacheLifetime.ThreeDays].
 */
enum class CacheLifetime(val seconds: Long) {
    OneDay(SEC_IN_DAY),
    TwoDays(2 * SEC_IN_DAY),
    ThreeDays(3 * SEC_IN_DAY),
    Week(DAYS_IN_WEEK * SEC_IN_DAY),
    TwoWeeks(2 * DAYS_IN_WEEK * SEC_IN_DAY),
    Month(DAYS_IN_MONTH * SEC_IN_DAY)
}
