package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.dto.UserProperty
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.qonversion.android.sdk.internal.di.DependenciesAssembly
import com.qonversion.android.sdk.internal.user.controller.UserController
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesController
import com.qonversion.android.sdk.listeners.EntitlementsUpdateListener

internal class QonversionInternal(
    private val internalConfig: InternalConfig,
    dependenciesAssembly: DependenciesAssembly,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Qonversion {

    private val userPropertiesController: UserPropertiesController =
        dependenciesAssembly.userPropertiesController()

    private val userController: UserController = dependenciesAssembly.userController()

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

    override fun setEntitlementsUpdateListener(entitlementsUpdateListener: EntitlementsUpdateListener) {
        internalConfig.entitlementsUpdateListener = entitlementsUpdateListener
    }

    override fun removeEntitlementsUpdateListener() {
        internalConfig.entitlementsUpdateListener = null
    }

    override fun finish() {
        internalConfig.entitlementsUpdateListener = null
        if (Qonversion.backingInstance == this) {
            Qonversion.backingInstance = null
        }
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
                val user = getUserInfo()
                onSuccess(user)
            } catch (exception: QonversionException) {
                onError(exception)
            }
        }
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        userPropertiesController.setProperty(property.code, value)
    }

    override fun setCustomUserProperty(key: String, value: String) {
        userPropertiesController.setProperty(key, value)
    }

    override fun setUserProperties(userProperties: Map<String, String>) {
        userPropertiesController.setProperties(userProperties)
    }
}
