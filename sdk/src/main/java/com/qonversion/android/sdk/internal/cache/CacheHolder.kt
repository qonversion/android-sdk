package com.qonversion.android.sdk.internal.cache

internal typealias Initializer<T> = (key: String) -> T

internal class CacheHolder<T : CachedObject<*>?>(
    private val initForKey: Initializer<T>
) : LinkedHashMap<String, T>() {

    override fun get(key: String): T? {
        if (!containsKey(key)) {
            put(key, initForKey(key))
        }

        return super.get(key)
    }
}
