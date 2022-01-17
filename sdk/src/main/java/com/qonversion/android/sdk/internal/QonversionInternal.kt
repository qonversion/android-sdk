package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.logger.LoggerConfig

internal class QonversionInternal(config: QonversionConfig) : Qonversion {

    init {
        InternalConfig.projectKey = config.projectKey
        InternalConfig.launchMode = config.launchMode
        InternalConfig.environment = config.environment

        val backgroundCacheLifetime = InternalCacheLifetime.from(config.backgroundCacheLifetime)
        InternalConfig.cacheLifetimeConfig = CacheLifetimeConfig(backgroundCacheLifetime)
        InternalConfig.loggerConfig = LoggerConfig(config.logLevel, config.logTag)
        InternalConfig.shouldConsumePurchases = config.shouldConsumePurchases
    }

    override fun setEnvironment(environment: Environment) {
        InternalConfig.environment = environment
    }

    override fun setLogLevel(logLevel: LogLevel) {
        val oldConfig = InternalConfig.loggerConfig
        InternalConfig.loggerConfig = LoggerConfig(logLevel, oldConfig.logTag)
    }

    override fun setLogTag(logTag: String) {
        val oldConfig = InternalConfig.loggerConfig
        InternalConfig.loggerConfig = LoggerConfig(oldConfig.logLevel, logTag)
    }

    override fun finish() {
        TODO("Not yet implemented")
    }
}
