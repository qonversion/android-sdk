package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.CacheLifetimeConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.user.controller.UserController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class QonversionInternal(
    config: QonversionConfig,
    private val internalConfig: InternalConfig,
    private val userController: UserController,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Qonversion {

    init {
        internalConfig.primaryConfig = config.primaryConfig
        internalConfig.storeConfig = config.storeConfig
        internalConfig.networkConfig = config.networkConfig

        val internalBackgroundCacheLifetime = InternalCacheLifetime.from(config.cacheLifetime)
        internalConfig.cacheLifetimeConfig = CacheLifetimeConfig(internalBackgroundCacheLifetime)

        internalConfig.loggerConfig = config.loggerConfig
    }

    override fun setEnvironment(environment: Environment) {
        internalConfig.primaryConfig = internalConfig.primaryConfig.copy(environment = environment)
    }

    override fun setLogLevel(logLevel: LogLevel) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logLevel = logLevel)
    }

    override fun setLogTag(logTag: String) {
        internalConfig.loggerConfig = internalConfig.loggerConfig.copy(logTag = logTag)
    }

    override fun setCacheLifetime(cacheLifetime: CacheLifetime) {
        val internalCacheLifetime = InternalCacheLifetime.from(cacheLifetime)
        internalConfig.cacheLifetimeConfig =
            internalConfig.cacheLifetimeConfig.copy(backgroundCacheLifetime = internalCacheLifetime)
    }

    override fun finish() {
        TODO("Not yet implemented")
    }

    override suspend fun getUserInfo(): User {
        return userController.getUser()
    }

    override fun getUserInfo(
        onSuccess: (user: User) -> Unit,
        onError: (exception: QonversionException) -> Unit
    ) {
        scope.launch {
            try {
                val user = userController.getUser()
                onSuccess(user)
            } catch (exception: QonversionException) {
                onError(exception)
            }
        }
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
}
