package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

internal interface CacheLifetimeConfigProvider {

    val cacheLifetimeConfig: CacheLifetimeConfig
}
