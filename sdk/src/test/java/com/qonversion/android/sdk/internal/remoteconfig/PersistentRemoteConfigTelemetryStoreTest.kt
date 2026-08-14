package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OCCURRED_AT_SECONDS = 1_700_000_000L
private const val RELEASE_7 = 7L
private const val STORE_BYTE_BUDGET = REMOTE_CONFIG_TELEMETRY_MAX_BYTES
private const val TINY_BUDGET = 4 * 1024

/**
 * The durable telemetry buffer: what survives a process, what is refused, and what the byte budget
 * does to an over-sized buffer.
 */
internal class PersistentRemoteConfigTelemetryStoreTest {
    private val scope = RemoteConfigSnapshotScope("project-secret", "env-production", "customer-secret")
    private val otherScope = RemoteConfigSnapshotScope("project-secret", "env-production", "other-secret")

    @Test
    fun `a buffer round-trips through a new store instance`() {
        val cache = MapCache()
        val events = listOf(decodeFailure("paywall_prices"), guardEvent())

        assertTrue(store(cache).save(scope, events))

        val persistedKey = cache.strings.keys.single()
        // The storage key is a salted digest: no identifier ever lands in a preference name.
        assertFalse(persistedKey.contains("project-secret"))
        assertFalse(persistedKey.contains("customer-secret"))
        assertEquals(events, store(cache).load(scope))
    }

    @Test
    fun `each identity addresses its own buffer`() {
        val cache = MapCache()
        val telemetryStore = store(cache)
        assertTrue(telemetryStore.save(scope, listOf(decodeFailure("paywall_prices"))))
        assertTrue(telemetryStore.save(otherScope, listOf(decodeFailure("onboarding"))))

        assertEquals(2, cache.strings.size)
        assertEquals("paywall_prices", telemetryStore.load(scope).single().logicalKey)
        assertEquals("onboarding", telemetryStore.load(otherScope).single().logicalKey)
    }

    @Test
    fun `clearing drops only the addressed identity`() {
        val cache = MapCache()
        val telemetryStore = store(cache)
        telemetryStore.save(scope, listOf(decodeFailure("paywall_prices")))
        telemetryStore.save(otherScope, listOf(decodeFailure("onboarding")))

        assertTrue(telemetryStore.clear(scope))

        assertTrue(telemetryStore.load(scope).isEmpty())
        assertEquals(1, telemetryStore.load(otherScope).size)
    }

    @Test
    fun `an empty buffer clears the record instead of writing one`() {
        val cache = MapCache()
        val telemetryStore = store(cache)
        telemetryStore.save(scope, listOf(decodeFailure("paywall_prices")))

        assertTrue(telemetryStore.save(scope, emptyList()))

        assertTrue(cache.strings.isEmpty())
    }

    @Test
    fun `the worst-case buffer fits the byte budget and round-trips whole`() {
        val cache = MapCache()
        // Every bucket the sender can hold, each with a logical key at the 200-byte contract
        // maximum: this is the largest record the store can ever be asked to write.
        val events = (0 until REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES).map { index ->
            decodeFailure(index.toString().padStart(REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES, 'k'))
        }

        assertTrue(store(cache).save(scope, events))

        val raw = requireNotNull(cache.strings.values.single())
        assertTrue("the byte budget was exceeded", raw.toByteArray(Charsets.UTF_8).size <= STORE_BYTE_BUDGET)
        // Nothing had to be shrunk away, so a restart resumes the whole buffer.
        assertEquals(events, store(cache).load(scope))
    }

    @Test
    fun `a buffer over the byte budget is shrunk rather than refused`() {
        val cache = MapCache()
        val events = (0 until REMOTE_CONFIG_TELEMETRY_MAX_ENTRIES).map { index ->
            decodeFailure(index.toString().padStart(REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES, 'k'))
        }
        // A budget the shipped one is comfortably above, so the shrinking path is exercised rather
        // than assumed: partial telemetry beats none.
        val tinyStore = PersistentRemoteConfigTelemetryStore(cache, Moshi.Builder().build(), maxBytes = TINY_BUDGET)

        assertTrue(tinyStore.save(scope, events))

        val raw = requireNotNull(cache.strings.values.single())
        assertTrue("the byte budget was exceeded", raw.toByteArray(Charsets.UTF_8).size <= TINY_BUDGET)
        val loaded = tinyStore.load(scope)
        assertTrue(loaded.isNotEmpty())
        assertTrue(loaded.size < events.size)
        // The oldest buckets are the ones kept: they are what the next flush would have sent first.
        assertEquals(events.take(loaded.size), loaded)
    }

    @Test
    fun `the entry bound is the sender's, not a second hardcoded one`() {
        val cache = MapCache()
        // A store that capped at its own constant would silently disagree with a sender configured
        // for a different bound — and the disagreement would only ever show up as lost events.
        val smallStore = PersistentRemoteConfigTelemetryStore(cache, Moshi.Builder().build(), maxEntries = 3)
        val events = (0 until 10).map { decodeFailure("key-$it") }

        assertTrue(smallStore.save(scope, events))

        assertEquals(events.take(3), smallStore.load(scope))
    }

    @Test
    fun `an event the gateway would refuse is never persisted`() {
        val cache = MapCache()

        val refused = listOf(
            decodeFailure("k".repeat(REMOTE_CONFIG_TELEMETRY_LOGICAL_KEY_MAX_BYTES + 1)),
            decodeFailure(""),
            // A non-decode kind may not name a key.
            guardEvent().copy(logicalKey = "paywall_prices"),
            decodeFailure("paywall_prices").copy(count = 0),
            decodeFailure("paywall_prices").copy(lastOccurredAtSeconds = 0),
            decodeFailure("paywall_prices").copy(releaseNumber = -1),
        )

        assertTrue(store(cache).save(scope, refused))
        assertTrue(cache.strings.isEmpty())
    }

    @Test
    fun `a malformed persisted buffer is dropped fail closed`() {
        val cache = MapCache()
        val telemetryStore = store(cache)
        telemetryStore.save(scope, listOf(decodeFailure("paywall_prices")))
        val persistedKey = cache.strings.keys.single()
        cache.strings[persistedKey] = "{\"version\":99,\"events\":[]}"

        assertTrue(telemetryStore.load(scope).isEmpty())
        assertFalse(cache.strings.containsKey(persistedKey))
    }

    @Test
    fun `an unknown persisted kind is skipped rather than replayed`() {
        val cache = MapCache()
        val telemetryStore = store(cache)
        telemetryStore.save(scope, listOf(decodeFailure("paywall_prices")))
        val persistedKey = cache.strings.keys.single()
        cache.strings[persistedKey] =
            "{\"version\":1,\"events\":[{\"kind\":\"from_the_future\",\"logical_key\":\"x\"," +
            "\"release_number\":1,\"count\":1,\"last_occurred_at\":$OCCURRED_AT_SECONDS}]}"

        // Sending it back would cost the whole batch a terminal 400.
        assertTrue(telemetryStore.load(scope).isEmpty())
    }

    private fun decodeFailure(key: String) = RemoteConfigTelemetryEvent(
        kind = RemoteConfigTelemetryKind.DecodeFailure,
        logicalKey = key,
        releaseNumber = RELEASE_7,
        count = 1,
        lastOccurredAtSeconds = OCCURRED_AT_SECONDS,
    )

    private fun guardEvent() = RemoteConfigTelemetryEvent(
        kind = RemoteConfigTelemetryKind.PreloadCorrupt,
        logicalKey = "",
        releaseNumber = 0,
        count = 3,
        lastOccurredAtSeconds = OCCURRED_AT_SECONDS,
    )

    private fun store(cache: Cache) = PersistentRemoteConfigTelemetryStore(cache, Moshi.Builder().build())

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
