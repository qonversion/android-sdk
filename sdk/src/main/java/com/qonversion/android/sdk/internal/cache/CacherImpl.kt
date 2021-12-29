package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.utils.msToSec
import kotlin.jvm.Throws
import java.util.Calendar

internal class CacherImpl<T : Any>(
    private val cacheMapper: CacheMapper<T>,
    private val storage: LocalStorage,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val backgroundCacheLifetime: CacheLifetime = CacheLifetime.THREE_DAYS,
    private val foregroundCacheLifetime: InternalCacheLifetime = InternalCacheLifetime.FIVE_MIN
) : Cacher<T> {

    var cachedObjects = CacheHolder { key -> load(key) }

    override fun store(key: String, value: T) {
        cachedObjects[key] = CachedObject(Calendar.getInstance().time, value).also {
            val mappedObject = cacheMapper.toString(it)
            storage.putString(key, mappedObject)
        }
    }

    override fun get(key: String) = cachedObjects[key]?.value

    override fun getActual(key: String) = cachedObjects[key]?.takeIf { isActual(it) }?.value

    override fun reset(key: String) {
        TODO("Not yet implemented")
    }

    @Throws(QonversionException::class)
    fun isActual(cachedObject: CachedObject<T>): Boolean {
        val currentTime = Calendar.getInstance().time
        val cachedTime = cachedObject.date
        val ageSec = (currentTime.time - cachedTime.time).msToSec()
        val lifetimeSec = if (appLifecycleObserver.isInBackground()) {
            backgroundCacheLifetime.seconds
        } else {
            foregroundCacheLifetime.seconds
        }
        return ageSec <= lifetimeSec
    }

    @Throws(QonversionException::class)
    fun load(key: String): CachedObject<T>? {
        val storedValue = storage.getString(key)
        return storedValue?.let { cacheMapper.fromString(storedValue) }
    }
}
