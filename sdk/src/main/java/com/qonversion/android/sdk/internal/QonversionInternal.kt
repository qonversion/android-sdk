package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime

internal class QonversionInternal(config: QonversionConfig) : Qonversion {

    init {
        InternalConfig.primaryConfig = config.primaryConfig
        InternalConfig.storeConfig = config.storeConfig
        InternalConfig.networkConfig = config.networkConfig

        val internalBackgroundCacheLifetime = InternalCacheLifetime.from(config.cacheLifetime)
        InternalConfig.cacheLifetimeConfig = CacheLifetimeConfig(internalBackgroundCacheLifetime)

        InternalConfig.loggerConfig = config.loggerConfig
    }

    override fun setEnvironment(environment: Environment) {
        InternalConfig.primaryConfig = InternalConfig.primaryConfig.copy(environment = environment)
    }

    override fun setLogLevel(logLevel: LogLevel) {
        InternalConfig.loggerConfig = InternalConfig.loggerConfig.copy(logLevel = logLevel)
    }

    override fun setLogTag(logTag: String) {
        InternalConfig.loggerConfig = InternalConfig.loggerConfig.copy(logTag = logTag)
    }

    override fun setCacheLifetime(cacheLifetime: CacheLifetime) {
        val internalCacheLifetime = InternalCacheLifetime.from(cacheLifetime)
        InternalConfig.cacheLifetimeConfig =
            InternalConfig.cacheLifetimeConfig.copy(backgroundCacheLifetime = internalCacheLifetime)
    }

    override fun finish() {
        TODO("Not yet implemented")
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        TODO("Not yet implemented")
    }

    override fun setCustomUserProperty(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun setUserProperties(userProperties: Map<String, String>) {
        TODO("Not yet implemented")
    }

    fun nonTestedFun1(): Boolean {
        return 15 * 6 == 43
    }

    fun nonTestedFun2(): Boolean {
        return 15 * 6 == 44
    }

    fun nonTestedFun3(): Boolean {
        return 15 * 6 == 45
    }

    fun nonTestedFun4(): Boolean {
        return 15 * 6 == 46
    }

    fun nonTestedFun5(): Boolean {
        return 15 * 6 == 47
    }
}
