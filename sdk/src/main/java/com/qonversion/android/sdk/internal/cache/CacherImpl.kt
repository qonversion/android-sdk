package com.qonversion.android.sdk.internal.cache

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.provider.CacheLifetimeConfigProvider
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.utils.msToSec
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.storage.UserDataProvider
import kotlin.jvm.Throws
import java.util.Calendar

internal class CacherImpl<T>(
    private val originalKey: String,
    private val userDataProvider: UserDataProvider,
    private val cacheMapper: CacheMapper<T>,
    private val storage: LocalStorage,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val cacheLifetimeConfigProvider: CacheLifetimeConfigProvider,
    logger: Logger
) : Cacher<T>, BaseClass(logger) {

    val key get() = "${userDataProvider.getUserId() ?: ""}_$originalKey"

    var cachedObjects: CacheHolder<CachedObject<T>?> = CacheHolder { key -> load(key) }

    override fun store(value: T) {
        val cachedObject = CachedObject(Calendar.getInstance().time, value)
        val mappedObject = cacheMapper.toSerializedString(cachedObject)
        storage.putString(key, mappedObject)
        cachedObjects[key] = cachedObject
    }

    override fun get(): T? = cachedObjects[key]?.value

    override fun getActual(cacheState: CacheState): T? {
        return cachedObjects[key]?.takeIf { isActual(it, cacheState) }?.value
    }

    override fun reset() {
        storage.remove(key)
        cachedObjects.remove(key)
    }

    @VisibleForTesting
    fun isActual(cachedObject: CachedObject<T>, cacheState: CacheState): Boolean {
        val currentTime = Calendar.getInstance().time
        val cachedTime = cachedObject.date
        val ageSec = (currentTime.time - cachedTime.time).msToSec()
        val lifetimeSec = getMaxCacheLifetimeSec(cacheState)

        return ageSec <= lifetimeSec
    }

    @Throws(QonversionException::class)
    @VisibleForTesting
    fun load(key: String): CachedObject<T>? {
        val storedValue = storage.getString(key)
        return storedValue?.let {
            try {
                cacheMapper.fromSerializedString(storedValue)
            } catch (exception: QonversionException) {
                logger.error("Failed to deserialized stored value $storedValue.", exception)
                null
            }
        }
    }

    @VisibleForTesting
    fun getMaxCacheLifetimeSec(cacheState: CacheState): Long {
        return if (appLifecycleObserver.isInBackground() || cacheState == CacheState.Error) {
            cacheLifetimeConfigProvider.cacheLifetimeConfig.backgroundCacheLifetime.seconds
        } else {
            cacheLifetimeConfigProvider.cacheLifetimeConfig.foregroundCacheLifetime.seconds
        }
    }
}
