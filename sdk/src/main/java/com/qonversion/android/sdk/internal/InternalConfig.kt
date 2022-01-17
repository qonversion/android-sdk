package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

import com.qonversion.android.sdk.internal.logger.LoggerConfig

internal object InternalConfig : EnvironmentProvider {
    var uid: String = ""
    var projectKey: String = ""
    var sdkVersion: String = BuildConfig.VERSION_NAME
    var launchMode: LaunchMode = LaunchMode.InfrastructureMode
    var requestsShouldBeDenied = false
    var loggerConfig = LoggerConfig()
    var cacheLifetimeConfig = CacheLifetimeConfig()
    override var environment = Environment.Production

    override val isSandbox get() = environment === Environment.Sandbox
}
