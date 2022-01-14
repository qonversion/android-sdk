package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

import com.qonversion.android.sdk.internal.logger.LoggerConfig

internal object InternalConfig {
    var uid: String = ""
    var projectKey: String = ""
    var sdkVersion: String = BuildConfig.VERSION_NAME
    var requestsShouldBeDenied = false
    var loggerConfig = LoggerConfig()
    var cacheLifetimeConfig = CacheLifetimeConfig()
    var environment = Environment.PRODUCTION

    val isSandbox get() = environment === Environment.SANDBOX
}
