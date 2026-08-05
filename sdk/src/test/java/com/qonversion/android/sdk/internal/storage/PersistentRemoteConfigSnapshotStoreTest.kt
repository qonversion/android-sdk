package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotApplyPolicy
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotEntry
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotRelease
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotScope
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigSnapshotState
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PersistentRemoteConfigSnapshotStoreTest {
    private val cache = SnapshotInMemoryCache()
    private val moshi = Moshi.Builder().build()
    private val userA = RemoteConfigSnapshotScope("project", "production", "canonical-user-a")
    private val userB = RemoteConfigSnapshotScope("project", "production", "canonical-user-b")

    @Test
    fun `candidate active and previous persist as one exact scoped versioned state`() {
        val state = RemoteConfigSnapshotState(
            candidate = release("three", 3, "candidate"),
            active = release("two", 2, "active"),
            previous = release("one", 1, "previous"),
            didActivate = true,
        )

        assertTrue(store().save(userA, state))

        assertEquals(1, cache.durableUpdates.size)
        assertEquals(2, cache.durableUpdates.single().values.size)
        assertTrue(cache.durableUpdates.single().values.containsKey(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY))
        assertTrue(cache.durableUpdates.single().values.containsKey(remoteConfigSnapshotStorageKey(userA)))
        val restarted = store().loadState(userA)
        assertEquals("three", restarted?.candidate?.releaseUid)
        assertEquals("two", restarted?.active?.releaseUid)
        assertEquals("one", restarted?.previous?.releaseUid)
        assertTrue(restarted?.didActivate == true)
    }

    @Test
    fun `store isolates canonical users and never migrates or mutates legacy LKG`() {
        cache.putString(LEGACY_LKG_KEY, "legacy-must-survive")
        val store = store()

        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))

        assertNull(store.loadState(userB))
        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertEquals("legacy-must-survive", cache.getString(LEGACY_LKG_KEY, null))
    }

    @Test
    fun `unknown or corrupt archive fails closed and clears only v2 storage`() {
        cache.putString(LEGACY_LKG_KEY, "legacy-must-survive")
        for (invalid in listOf(
            "{not-json",
            "{\"version\":999,\"records\":[]}",
            "{\"version\":1,\"records\":[{\"unexpected\":true}]}",
        )) {
            val storageKey = remoteConfigSnapshotStorageKey(userA)
            cache.putString(storageKey, invalid)
            cache.putString(
                REMOTE_CONFIG_SNAPSHOT_INDEX_KEY,
                "{\"version\":1,\"storageKeys\":[\"$storageKey\"]}",
            )

            assertNull(store().loadState(userA))

            assertNull(cache.getString(storageKey, null))
            assertEquals("legacy-must-survive", cache.getString(LEGACY_LKG_KEY, null))
        }
    }

    @Test
    fun `failed durable save preserves prior whole state across restart`() {
        val store = store()
        val prior = RemoteConfigSnapshotState(
            candidate = release("two", 2, "candidate"),
            active = release("one", 1, "active"),
            didActivate = true,
        )
        assertTrue(store.save(userA, prior))
        cache.nextDurableUpdateResult = false

        val replacement = RemoteConfigSnapshotState(
            candidate = release("three", 3, "replacement"),
            active = release("three", 3, "replacement"),
            previous = release("one", 1, "active"),
            didActivate = true,
        )
        assertFalse(store.save(userA, replacement))

        val restarted = store().loadState(userA)
        assertEquals("two", restarted?.candidate?.releaseUid)
        assertEquals("one", restarted?.active?.releaseUid)
        assertNull(restarted?.previous)
    }

    @Test
    fun `oversized replacement is rejected before durable storage changes`() {
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi, maxStateBytes = 600)
        val prior = RemoteConfigSnapshotState(candidate = release("one", 1, "small"))
        assertTrue(store.save(userA, prior))
        val writesBefore = cache.durableUpdates.size
        val oversized = RemoteConfigSnapshotState(
            candidate = release("two", 2, "x".repeat(2_000)),
        )

        assertFalse(store.save(userA, oversized))

        assertEquals(writesBefore, cache.durableUpdates.size)
        assertEquals("one", store().loadState(userA)?.candidate?.releaseUid)
    }

    @Test
    fun `scope archive is bounded and evicts least recently used scope atomically`() {
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi, maxScopes = 2)
        val userC = RemoteConfigSnapshotScope("project", "production", "canonical-user-c")
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "a"))))
        assertTrue(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "b"))))
        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertTrue(store.save(userC, RemoteConfigSnapshotState(candidate = release("c", 1, "c"))))

        assertNull(store.loadState(userB))
        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertEquals("c", store.loadState(userC)?.candidate?.releaseUid)
    }

    @Test
    fun `default scope archive retains exactly sixteen most recently used identities`() {
        val store = store()
        val users = List(17) { index ->
            RemoteConfigSnapshotScope("project", "production", "canonical-user-$index")
        }
        users.take(16).forEachIndexed { index, scope ->
            assertTrue(store.save(scope, RemoteConfigSnapshotState(candidate = release("r$index", 1, "$index"))))
        }
        assertEquals("r0", store.loadState(users.first())?.candidate?.releaseUid)

        assertTrue(store.save(users.last(), RemoteConfigSnapshotState(candidate = release("r16", 1, "16"))))

        assertNull(store.loadState(users[1]))
        assertEquals("r0", store.loadState(users.first())?.candidate?.releaseUid)
        assertEquals("r16", store.loadState(users.last())?.candidate?.releaseUid)
    }

    @Test
    fun `saving another identity never rewrites the first identity payload`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.durableUpdates.clear()

        assertTrue(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "private-b"))))

        val update = cache.durableUpdates.single()
        assertFalse(update.values.containsKey(remoteConfigSnapshotStorageKey(userA)))
        assertTrue(update.values.containsKey(remoteConfigSnapshotStorageKey(userB)))
        assertTrue(update.values.containsKey(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY))
    }

    @Test
    fun `untrusted index can never remove an unrelated preference`() {
        val unrelatedKey = "customer_auth_token"
        cache.putString(unrelatedKey, "must-survive")
        cache.putString(
            REMOTE_CONFIG_SNAPSHOT_INDEX_KEY,
            "{\"version\":1,\"storageKeys\":[\"$unrelatedKey\"]}",
        )

        assertTrue(store().save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "a"))))

        assertEquals("must-survive", cache.getString(unrelatedKey, null))
        assertFalse(cache.durableUpdates.last().removedKeys.contains(unrelatedKey))
    }

    @Test
    fun `envelope self declared scope cannot cross the requested privacy scope`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        val userAKey = remoteConfigSnapshotStorageKey(userA)
        cache.strings[userAKey] = requireNotNull(cache.strings[userAKey])
            .replace("canonical-user-a", "canonical-user-b")

        assertNull(store.loadState(userA))
        assertNull(store.loadState(userB))
    }

    @Test
    fun `oversized corrupt envelope cannot evict an admitted identity`() {
        val userBKey = remoteConfigSnapshotStorageKey(userB)
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi, maxScopes = 2)
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        assertTrue(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "private-b"))))
        cache.strings[userBKey] = requireNotNull(cache.strings[userBKey])
            .replace("canonical-user-b", "x".repeat(257))

        assertNull(store.loadState(userB))

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertNull(cache.getString(userBKey, null))
    }

    @Test
    fun `saving a new identity evicts corrupt envelope before admitted identity`() {
        val userC = RemoteConfigSnapshotScope("project", "production", "canonical-user-c")
        val userBKey = remoteConfigSnapshotStorageKey(userB)
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi, maxScopes = 2)
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        assertTrue(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "private-b"))))
        cache.strings[userBKey] = requireNotNull(cache.strings[userBKey])
            .replace("canonical-user-b", "x".repeat(257))

        assertTrue(store.save(userC, RemoteConfigSnapshotState(candidate = release("c", 1, "private-c"))))

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertNull(store.loadState(userB))
        assertEquals("c", store.loadState(userC)?.candidate?.releaseUid)
    }

    @Test
    fun `failed durable read promotion never blocks a valid snapshot`() {
        val store = PersistentRemoteConfigSnapshotStore(cache, moshi, maxScopes = 2)
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        assertTrue(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "private-b"))))
        cache.nextDurableUpdateResult = false

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertTrue(cache.nextDurableUpdateResult)
    }

    @Test
    fun `transient index read failure never deletes admitted state`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.throwOnNextGet += REMOTE_CONFIG_SNAPSHOT_INDEX_KEY

        val failedLoad = store.load(userA)
        assertEquals(RemoteConfigSnapshotLoadStatus.Failed, failedLoad.status)
        assertNull(failedLoad.state)

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
    }

    @Test
    fun `transient envelope read failure never deletes admitted state`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.throwOnNextGet += remoteConfigSnapshotStorageKey(userA)

        val failedLoad = store.load(userA)
        assertEquals(RemoteConfigSnapshotLoadStatus.Failed, failedLoad.status)
        assertNull(failedLoad.state)

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
    }

    @Test
    fun `transient index read failure rejects save without orphaning admitted state`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.throwOnNextGet += REMOTE_CONFIG_SNAPSHOT_INDEX_KEY

        assertFalse(store.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "private-b"))))

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)
        assertNull(store.loadState(userB))
    }

    @Test
    fun `corrupt index is rebuilt from an exact valid envelope without losing state`() {
        val store = store()
        val storageKey = remoteConfigSnapshotStorageKey(userA)
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.putString(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY, "{not-json")

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)

        assertTrue(cache.getString(storageKey, null)?.isNotEmpty() == true)
        assertEquals("a", store().loadState(userA)?.candidate?.releaseUid)
    }

    @Test
    fun `missing index is rebuilt from an exact valid envelope without losing state`() {
        val store = store()
        assertTrue(store.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "private-a"))))
        cache.remove(REMOTE_CONFIG_SNAPSHOT_INDEX_KEY)

        assertEquals("a", store.loadState(userA)?.candidate?.releaseUid)

        assertEquals("a", store().loadState(userA)?.candidate?.releaseUid)
    }

    @Test
    fun `corrupt candidate is discarded without erasing valid active and previous`() {
        val store = store()
        val storageKey = remoteConfigSnapshotStorageKey(userA)
        val state = RemoteConfigSnapshotState(
            candidate = release("three", 3, "candidate"),
            active = release("two", 2, "active"),
            previous = release("one", 1, "previous"),
            didActivate = true,
        )
        assertTrue(store.save(userA, state))
        cache.strings[storageKey] = requireNotNull(cache.strings[storageKey]).replace(
            "\"releaseUid\":\"three\"",
            "\"releaseUid\":\"${"x".repeat(37)}\"",
        )

        val salvaged = store.loadState(userA)

        assertNull(salvaged?.candidate)
        assertEquals("two", salvaged?.active?.releaseUid)
        assertEquals("one", salvaged?.previous?.releaseUid)
        val restarted = store().loadState(userA)
        assertNull(restarted?.candidate)
        assertEquals("two", restarted?.active?.releaseUid)
        assertEquals("one", restarted?.previous?.releaseUid)
    }

    @Test
    fun `equal number conflicting candidate is dropped and canonical active survives`() {
        val store = store()
        val storageKey = remoteConfigSnapshotStorageKey(userA)
        val active = release("two", 2, "active")
        assertTrue(
            store.save(
                userA,
                RemoteConfigSnapshotState(
                    candidate = active,
                    active = active,
                    previous = release("one", 1, "previous"),
                    didActivate = true,
                ),
            ),
        )
        cache.strings[storageKey] = requireNotNull(cache.strings[storageKey]).replaceFirst(
            "\"variationUid\":\"variation-two\"",
            "\"variationUid\":\"variation-conflict\"",
        )

        val salvaged = store.loadState(userA)

        assertNull(salvaged?.candidate)
        assertEquals("two", salvaged?.active?.releaseUid)
        assertEquals("variation-two", salvaged?.active?.entry("key")?.variationUid)
        assertNull(store().loadState(userA)?.candidate)
    }

    @Test
    fun `global persisted byte budget evicts least recently used envelopes`() {
        val probe = store()
        assertTrue(probe.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "a"))))
        val oneEnvelopeBytes = requireNotNull(cache.strings[remoteConfigSnapshotStorageKey(userA)])
            .toByteArray(Charsets.UTF_8).size
        cache.strings.clear()
        cache.durableUpdates.clear()
        val userC = RemoteConfigSnapshotScope("project", "production", "canonical-user-c")
        val boundedStore = PersistentRemoteConfigSnapshotStore(
            cache = cache,
            moshi = moshi,
            maxTotalBytes = oneEnvelopeBytes * 2,
        )
        assertTrue(boundedStore.save(userA, RemoteConfigSnapshotState(candidate = release("a", 1, "a"))))
        assertTrue(boundedStore.save(userB, RemoteConfigSnapshotState(candidate = release("b", 1, "b"))))

        assertTrue(boundedStore.save(userC, RemoteConfigSnapshotState(candidate = release("c", 1, "c"))))

        assertNull(boundedStore.loadState(userA))
        assertEquals("b", boundedStore.loadState(userB)?.candidate?.releaseUid)
        assertEquals("c", boundedStore.loadState(userC)?.candidate?.releaseUid)
        val persistedBytes = listOf(userA, userB, userC).sumOf { scope ->
            cache.strings[remoteConfigSnapshotStorageKey(scope)]?.toByteArray(Charsets.UTF_8)?.size ?: 0
        }
        assertTrue(persistedBytes <= oneEnvelopeBytes * 2)
    }

    private fun store() = PersistentRemoteConfigSnapshotStore(cache, moshi)

    private fun PersistentRemoteConfigSnapshotStore.loadState(
        scope: RemoteConfigSnapshotScope,
    ): RemoteConfigSnapshotState? = load(scope).state

    private fun release(uid: String, number: Long, value: String) = RemoteConfigSnapshotRelease(
        releaseUid = uid,
        releaseNumber = number,
        manifestContentHash = "a".repeat(64),
        entries = listOf(
            RemoteConfigSnapshotEntry.value(
                key = "key",
                rawValue = "\"$value\"".encodeToByteArray(),
                variationUid = "variation-$uid",
                applyPolicy = RemoteConfigSnapshotApplyPolicy.OnNextActivate,
                metadata = null,
            ),
        ),
    )

    private class SnapshotInMemoryCache : Cache {
        data class DurableUpdate(val values: Map<String, String?>, val removedKeys: Set<String>)

        val strings = mutableMapOf<String, String?>()
        val durableUpdates = mutableListOf<DurableUpdate>()
        var nextDurableUpdateResult = true
        val throwOnNextGet = mutableSetOf<String>()
        private val values = mutableMapOf<String, Any?>()

        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getBool(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun putBool(key: String, value: Boolean) { values[key] = value }
        override fun putFloat(key: String, value: Float) { values[key] = value }
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun putLong(key: String, value: Long) { values[key] = value }
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun putString(key: String, value: String?) { strings[key] = value }
        override fun getString(key: String, defValue: String?): String? {
            if (throwOnNextGet.remove(key)) error("transient read failure")
            return strings[key] ?: defValue
        }
        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) {
            putString(key, adapter.toJson(value))
        }
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? =
            getString(key, null)?.let(adapter::fromJson)
        override fun remove(key: String) { strings.remove(key); values.remove(key) }
        override fun updateStringsDurably(
            values: Map<String, String?>,
            removedKeys: Set<String>,
        ): Boolean {
            val result = nextDurableUpdateResult
            nextDurableUpdateResult = true
            if (!result) return false
            durableUpdates += DurableUpdate(values.toMap(), removedKeys.toSet())
            removedKeys.forEach(strings::remove)
            strings.putAll(values)
            return true
        }
    }

    private companion object {
        const val LEGACY_LKG_KEY = "qonversion_remote_config_lkg_index"
    }
}
