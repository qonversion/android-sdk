package com.qonversion.android.sdk.internal.cache

internal data class CacheLifetimeConfig(
    val backgroundCacheLifetime: InternalCacheLifetime = InternalCacheLifetime.THREE_DAYS,
    val foregroundCacheLifetime: InternalCacheLifetime = InternalCacheLifetime.FIVE_MIN
)
