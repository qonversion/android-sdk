package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistentRemoteConfigFetchPolicyStoreTest {
    private val scope = RemoteConfigSnapshotScope("project-secret", "production", "customer-secret")
    private val policyScope = RemoteConfigFetchPolicyScope.from(scope)

    @Test
    fun `policy state is durably scoped and survives a new store instance`() {
        val cache = MapCache()
        val first = store(cache)
        val state = RemoteConfigFetchPolicyState(
            lastSuccessfulFetchAtMillis = 12,
            consecutiveRetryableFailures = 3,
            nextAllowedFetchAtMillis = 34,
        )

        assertTrue(first.save(policyScope, state))
        val persistedKey = cache.strings.keys.single()
        assertFalse(persistedKey.contains("project-secret"))
        assertFalse(persistedKey.contains("customer-secret"))

        assertEquals(state, store(cache).load(policyScope))
    }

    @Test
    fun `malformed or unbounded persisted policy is removed fail closed`() {
        val cache = MapCache()
        val policyStore = store(cache)
        assertTrue(policyStore.save(policyScope, RemoteConfigFetchPolicyState()))
        val persistedKey = cache.strings.keys.single()
        cache.strings[persistedKey] =
            "{\"version\":1,\"last_successful_fetch_at_millis\":0," +
            "\"consecutive_retryable_failures\":999,\"next_allowed_fetch_at_millis\":0}"

        assertNull(policyStore.load(policyScope))
        assertFalse(cache.strings.containsKey(persistedKey))
    }

    private fun store(cache: Cache) = PersistentRemoteConfigFetchPolicyStore(
        cache = cache,
        moshi = Moshi.Builder().build(),
    )

    private class MapCache : Cache {
        val strings = mutableMapOf<String, String?>()
        private val longs = mutableMapOf<String, Long>()
        private val ints = mutableMapOf<String, Int>()
        private val bools = mutableMapOf<String, Boolean>()
        private val floats = mutableMapOf<String, Float>()

        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun getInt(key: String, defValue: Int): Int = ints[key] ?: defValue
        override fun getBool(key: String, defValue: Boolean): Boolean = bools[key] ?: defValue
        override fun putBool(key: String, value: Boolean) { bools[key] = value }
        override fun putFloat(key: String, value: Float) { floats[key] = value }
        override fun getFloat(key: String, defValue: Float): Float = floats[key] ?: defValue
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun getLong(key: String, defValue: Long): Long = longs[key] ?: defValue
        override fun putString(key: String, value: String?) { strings[key] = value }
        override fun getString(key: String, defValue: String?): String? = strings[key] ?: defValue
        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) {
            strings[key] = adapter.toJson(value)
        }
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? =
            strings[key]?.let(adapter::fromJson)
        override fun remove(key: String) { strings.remove(key) }
        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
            removedKeys.forEach(strings::remove)
            strings.putAll(values)
            return true
        }
    }
}
