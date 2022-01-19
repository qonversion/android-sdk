package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

internal interface CacheLifetimeConfigProvider {

    val cacheLifetimeConfig: CacheLifetimeConfig
}
