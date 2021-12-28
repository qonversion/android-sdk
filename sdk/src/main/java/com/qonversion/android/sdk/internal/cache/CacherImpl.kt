package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.serializers.mappers.cache.CacheMapper
import com.qonversion.android.sdk.internal.utils.msToSec
import kotlin.jvm.Throws
import java.util.Calendar

internal class CacherImpl<T : Any>(
    private val cacheMapper: CacheMapper<T>,
    private val storage: LocalStorage,
    private val storageKey: String,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val backgroundCacheLifetime: CacheLifetime = CacheLifetime.THREE_DAYS,
    private val foregroundCacheLifetimeSec: Long
) : Cacher<T> {

    var cachedObject: CachedObject<T>? = null

    override fun store(value: T) {
        val mappedObject = cacheMapper.toJson(value)
        storage.putString(storageKey, mappedObject)
    }

    override fun get(): T? {
        val isCacheActual = cachedObject?.let { cachedObject ->
            val currentTime = Calendar.getInstance().time
            val cachedTime = cachedObject.date
            val distanceSec = (currentTime.time - cachedTime.time).msToSec()
            val lifetimeSec = if (appLifecycleObserver.isInBackground()) {
                backgroundCacheLifetime.seconds
            } else {
                foregroundCacheLifetimeSec
            }
            return@let lifetimeSec >= distanceSec
        } == true

        if (!isCacheActual) {
            cachedObject = load()
        }

        return cachedObject?.value
    }

    @Throws(QonversionException::class)
    fun load(): CachedObject<T> {
        val storedValue = storage.getString(storageKey)
        val mappedObject = storedValue?.let { cacheMapper.fromJson(storedValue) }
        return CachedObject(Calendar.getInstance().time, mappedObject)
    }
}
