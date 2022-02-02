package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

import com.qonversion.android.sdk.internal.networkLayer.NetworkConfigHolder
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider

internal object InternalConfig :
    EnvironmentProvider,
    LoggerConfigProvider,
    CacheLifetimeConfigProvider,
    NetworkConfigHolder {
    var uid: String = ""

    lateinit var primaryConfig: PrimaryConfig
    lateinit var storeConfig: StoreConfig
    lateinit var networkConfig: NetworkConfig
    lateinit var loggerConfig: LoggerConfig

    override var cacheLifetimeConfig = CacheLifetimeConfig()

    override val environment
        get() = primaryConfig.environment

    override val isSandbox get() = environment === Environment.Sandbox

    override val logLevel: LogLevel
        get() = loggerConfig.logLevel

    override val logTag: String
        get() = loggerConfig.logTag

    override var canSendRequests: Boolean
        get() = networkConfig.canSendRequests
        set(value) {
            networkConfig.canSendRequests = value
        }
}
