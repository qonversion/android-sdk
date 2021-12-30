package com.qonversion.android.sdk.internal.cache

internal typealias Initializer<T> = (key: String) -> CachedObject<T>?

internal class CacheHolder<T>(
    private val initForKey: Initializer<T>
) : LinkedHashMap<String, CachedObject<T>?>() {

    override fun get(key: String): CachedObject<T>? {
        if (!containsKey(key)) {
            put(key, initForKey(key))
        }

        return super.get(key)
    }
}
