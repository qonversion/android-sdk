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
    private val storageKey: String,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val backgroundCacheLifetime: CacheLifetime = CacheLifetime.THREE_DAYS,
    private val foregroundCacheLifetime: InternalCacheLifetime = InternalCacheLifetime.FIVE_MIN
) : Cacher<T> {

    var shouldLoadFromStorage = true
    var cachedObject: CachedObject<T>? = null
        get() {
            if (shouldLoadFromStorage) {
                field = load()
            }
            return field
        }
        set(value) {
            shouldLoadFromStorage = false
            field = value
        }

    override fun store(value: T) {
        cachedObject = CachedObject(Calendar.getInstance().time, value).also {
            val mappedObject = cacheMapper.toString(it)
            storage.putString(storageKey, mappedObject)
        }
    }

    override fun get() = cachedObject?.value

    override fun getActual() = if (isActual()) cachedObject?.value else null

    override fun isActual(): Boolean {
        val nonNullCachedObject = cachedObject ?: return false
        val currentTime = Calendar.getInstance().time
        val cachedTime = nonNullCachedObject.date
        val ageSec = (currentTime.time - cachedTime.time).msToSec()
        val lifetimeSec = if (appLifecycleObserver.isInBackground()) {
            backgroundCacheLifetime.seconds
        } else {
            foregroundCacheLifetime.seconds
        }
        return ageSec <= lifetimeSec
    }

    @Throws(QonversionException::class)
    fun load(): CachedObject<T>? {
        val storedValue = storage.getString(storageKey)
        return storedValue?.let { cacheMapper.fromString(storedValue) }
    }
}
