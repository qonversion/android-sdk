package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.utils.msToSec
import com.qonversion.android.sdk.internal.logger.Logger
import kotlin.jvm.Throws
import java.util.Calendar

internal class CacherImpl<T : Any>(
    private val cacheMapper: CacheMapper<T>,
    private val storage: LocalStorage,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val cacheLifetimeConfig: CacheLifetimeConfig,
    logger: Logger
) : Cacher<T>, BaseClass(logger) {

    var cachedObjects: CacheHolder<CachedObject<T>?> = CacheHolder { key -> load(key) }

    override fun store(key: String, value: T) {
        val cachedObject = CachedObject(Calendar.getInstance().time, value)
        val mappedObject = cacheMapper.toSerializedString(cachedObject)
        storage.putString(key, mappedObject)
        cachedObjects[key] = cachedObject
    }

    override fun get(key: String): T? = cachedObjects[key]?.value

    override fun getActual(key: String): T? = cachedObjects[key]?.takeIf { isActual(it) }?.value

    override fun reset(key: String) {
        storage.remove(key)
        cachedObjects.remove(key)
    }

    fun isActual(cachedObject: CachedObject<T>): Boolean {
        val currentTime = Calendar.getInstance().time
        val cachedTime = cachedObject.date
        val ageSec = (currentTime.time - cachedTime.time).msToSec()
        val lifetimeSec = if (appLifecycleObserver.isInBackground()) {
            cacheLifetimeConfig.backgroundCacheLifetime.seconds
        } else {
            cacheLifetimeConfig.foregroundCacheLifetime.seconds
        }
        return ageSec <= lifetimeSec
    }

    @Throws(QonversionException::class)
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
}
