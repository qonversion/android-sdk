package com.qonversion.android.sdk.internal.cache

internal data class CacheLifetimeConfig(
    val backgroundCacheLifetime: InternalCacheLifetime,
    val foregroundCacheLifetime: InternalCacheLifetime = InternalCacheLifetime.FiveMin
)
