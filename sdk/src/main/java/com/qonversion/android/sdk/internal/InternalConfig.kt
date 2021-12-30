package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

internal object InternalConfig {
    var uid: String = ""
    var projectKey: String = ""
    var sdkVersion: String = ""
    var debugMode: Boolean = false
    var requestsShouldBeDenied: Boolean = false
    var cacheLifetimeConfig: CacheLifetimeConfig = CacheLifetimeConfig()
}
