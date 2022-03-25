package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.config.LoggerConfig
import com.qonversion.android.sdk.config.NetworkConfig
import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.config.StoreConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime

import com.qonversion.android.sdk.internal.provider.NetworkConfigHolder
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.LoggerConfigProvider
import com.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import com.qonversion.android.sdk.internal.provider.UidProvider
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener

internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    val storeConfig: StoreConfig,
    val networkConfig: NetworkConfig,
    var loggerConfig: LoggerConfig,
    override var cacheLifetimeConfig: CacheLifetimeConfig,
    override var entitlementsUpdateListener: EntitlementsUpdateListener? = null
) : EnvironmentProvider,
    LoggerConfigProvider,
    CacheLifetimeConfigProvider,
    NetworkConfigHolder,
    PrimaryConfigProvider,
    UidProvider,
    EntitlementsUpdateListenerProvider {

    override var uid: String = ""

    constructor(qonversionConfig: QonversionConfig) : this(
        qonversionConfig.primaryConfig,
        qonversionConfig.storeConfig,
        qonversionConfig.networkConfig,
        qonversionConfig.loggerConfig,
        qonversionConfig.cacheLifetime.run {
            val internalBackgroundCacheLifetime = InternalCacheLifetime.from(this)
            CacheLifetimeConfig(internalBackgroundCacheLifetime)
        },
        qonversionConfig.entitlementsUpdateListener
    )

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
