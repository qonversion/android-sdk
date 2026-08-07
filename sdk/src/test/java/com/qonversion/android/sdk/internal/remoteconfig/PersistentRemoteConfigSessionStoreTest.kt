package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistentRemoteConfigSessionStoreTest {
    private val scope = RemoteConfigSnapshotScope("project-secret", "env-production", "customer-secret")
    private val otherIdentity = RemoteConfigSnapshotScope("project-secret", "env-production", "other-secret")
    private val session = RemoteConfigGatewaySession(
        token = "qrcs1.session-secret",
        projectId = 42,
        environment = "prod",
        expiresAtMillis = 1_800_000_000_000,
    )

    @Test
    fun `session is durably scoped and survives a new store instance`() {
        val cache = MapCache()
        assertTrue(store(cache).save(scope, session))

        val persistedKey = cache.strings.keys.single()
        assertFalse(persistedKey.contains("project-secret"))
        assertFalse(persistedKey.contains("customer-secret"))
        assertEquals(session, store(cache).load(scope))
    }

    @Test
    fun `an identity change addresses a different record and never reads the previous token`() {
        val cache = MapCache()
        assertTrue(store(cache).save(scope, session))

        assertNull(store(cache).load(otherIdentity))
        assertTrue(store(cache).save(otherIdentity, session.copy(token = "qrcs1.other")))
        assertEquals(2, cache.strings.size)
        assertEquals("qrcs1.session-secret", store(cache).load(scope)?.token)
        assertEquals("qrcs1.other", store(cache).load(otherIdentity)?.token)
    }

    @Test
    fun `clearing drops only the addressed identity`() {
        val cache = MapCache()
        val sessionStore = store(cache)
        assertTrue(sessionStore.save(scope, session))
        assertTrue(sessionStore.save(otherIdentity, session.copy(token = "qrcs1.other")))

        assertTrue(sessionStore.clear(scope))

        assertNull(sessionStore.load(scope))
        assertEquals("qrcs1.other", sessionStore.load(otherIdentity)?.token)
    }

    @Test
    fun `malformed persisted session is removed fail closed`() {
        val cache = MapCache()
        val sessionStore = store(cache)
        assertTrue(sessionStore.save(scope, session))
        val persistedKey = cache.strings.keys.single()
        cache.strings[persistedKey] =
            "{\"version\":1,\"session_token\":\"\",\"project_id\":42," +
            "\"environment\":\"prod\",\"expires_at_millis\":1}"

        assertNull(sessionStore.load(scope))
        assertFalse(cache.strings.containsKey(persistedKey))
    }

    @Test
    fun `a session that cannot be described is refused rather than half written`() {
        val cache = MapCache()

        assertFalse(store(cache).save(scope, session.copy(token = "")))
        assertFalse(store(cache).save(scope, session.copy(projectId = 0)))
        assertFalse(store(cache).save(scope, session.copy(expiresAtMillis = 0)))
        assertTrue(cache.strings.isEmpty())
    }

    private fun store(cache: Cache) = PersistentRemoteConfigSessionStore(cache, Moshi.Builder().build())

    private class MapCache : Cache {
        val strings = mutableMapOf<String, String?>()

        override fun putInt(key: String, value: Int) = Unit
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getBool(key: String, defValue: Boolean): Boolean = defValue
        override fun putBool(key: String, value: Boolean) = Unit
        override fun putFloat(key: String, value: Float) = Unit
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun putLong(key: String, value: Long) = Unit
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun putString(key: String, value: String?) { strings[key] = value }
        override fun getString(key: String, defValue: String?): String? = strings[key] ?: defValue
        override fun remove(key: String) { strings.remove(key) }

        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
            removedKeys.forEach(strings::remove)
            strings.putAll(values)
            return true
        }

        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) = Unit
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? = null
    }
}
