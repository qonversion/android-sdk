package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

import com.qonversion.android.sdk.internal.logger.LoggerConfig

internal object InternalConfig {
    var uid: String = ""
    var projectKey: String = ""
    var sdkVersion: String = BuildConfig.VERSION_NAME
    var debugMode: Boolean = false
    var requestsShouldBeDenied: Boolean = false
    var loggerConfig = LoggerConfig()
    var cacheLifetimeConfig: CacheLifetimeConfig = CacheLifetimeConfig()
}
