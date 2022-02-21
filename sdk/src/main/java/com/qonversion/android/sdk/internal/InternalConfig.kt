package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig

import com.qonversion.android.sdk.internal.provider.NetworkConfigHolder
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider
import com.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import com.qonversion.android.sdk.internal.provider.UidProvider
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener

internal object InternalConfig :
    EnvironmentProvider,
    LoggerConfigProvider,
    CacheLifetimeConfigProvider,
    NetworkConfigHolder,
    PrimaryConfigProvider,
    UidProvider,
    EntitlementsUpdateListenerProvider {
    override var uid: String = ""

    override lateinit var primaryConfig: PrimaryConfig
    lateinit var storeConfig: StoreConfig
    lateinit var networkConfig: NetworkConfig
    lateinit var loggerConfig: LoggerConfig
    override lateinit var cacheLifetimeConfig: CacheLifetimeConfig
    override var entitlementsUpdateListener: EntitlementsUpdateListener? = null

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
