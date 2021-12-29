package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.internal.utils.DAYS_IS_MONTH
import com.qonversion.android.sdk.internal.utils.DAYS_IS_WEEK
import com.qonversion.android.sdk.internal.utils.SEC_IN_DAY

/**
 * The Qonversion SDK caches some information from the billing library or API.
 * This enum contains different available settings for cache lifetime.
 * Provide it to the configuration object via (
 * TODO add method / object reference
 * ) while initializing the SDK.
 * The provided value is used only for background requests (when the app is
 * in the background). Cache lifetime for foreground requests is much less than
 * for background ones and is not configurable. Let's say we have user info
 * loaded and cached a day before yesterday. If the cache lifetime is set
 * to [CacheLifetime.THREE_DAYS] and you request user info when the app is in
 * the background then the cached value will be returned. But if you request it
 * from the foreground app or the cache lifetime is set to [CacheLifetime.ONE_DAY],
 * then cached data will be renewed and then returned.
 *
 * The default value is [CacheLifetime.THREE_DAYS].
 */
enum class CacheLifetime(val seconds: Long) {
    ONE_DAY(SEC_IN_DAY),
    TWO_DAYS(2 * SEC_IN_DAY),
    THREE_DAYS(3 * SEC_IN_DAY),
    WEEK(DAYS_IS_WEEK * SEC_IN_DAY),
    TWO_WEEKS(2 * DAYS_IS_WEEK * SEC_IN_DAY),
    MONTH(DAYS_IS_MONTH * SEC_IN_DAY)
}
