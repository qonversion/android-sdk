package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigurationAssignmentType
import com.qonversion.android.sdk.dto.QRemoteConfigurationSource
import com.qonversion.android.sdk.dto.QRemoteConfigurationSourceType
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.dto.QRemoteConfigurationSourceAssignmentTypeAdapter
import com.qonversion.android.sdk.internal.dto.QRemoteConfigurationSourceTypeAdapter
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

internal class PersistentRemoteConfigCacheTest {
    private val backingCache = InMemoryCache()
    private val config = internalConfig(projectKey = "project-a", userId = "user-a")
    private val moshi = Moshi.Builder()
        .add(QRemoteConfigurationSourceTypeAdapter())
        .add(QRemoteConfigurationSourceAssignmentTypeAdapter())
        .build()
    private val directExecutor = Executor { it.run() }

    @Test
    fun `saved config survives cache recreation`() {
        val firstProcess = cache(config)
        val expected = remoteConfig(contextKey = "paywall", payloadValue = "v1")

        firstProcess.save(expected)

        val restartedProcess = cache(config)
        assertEquals(expected, restartedProcess.get("paywall"))
        assertEquals(listOf(expected), restartedProcess.getAll().remoteConfigs)
    }

    @Test
    fun `empty context last known good is canonical across process restart`() {
        val expected = remoteConfig(contextKey = "", payloadValue = "empty-context")

        cache(config).save(expected)

        val restartedProcess = cache(config)
        assertEquals(expected, restartedProcess.get(null))
        assertEquals(expected, restartedProcess.get(""))
        assertEquals(listOf(expected), restartedProcess.getAll().remoteConfigs)
    }

    @Test
    fun `payload and bounded index are persisted in one cache transaction`() {
        val cache = cache(config)

        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "v1"))

        assertEquals(1, backingCache.batchUpdates.size)
        val update = backingCache.batchUpdates.single()
        assertEquals(2, update.values.size)
        assertTrue(update.values.values.any { it?.contains("\"remoteConfigs\"") == true })
        assertTrue(update.values.values.any { it?.contains("\"scopes\"") == true })
    }

    @Test
    fun `cache is isolated by project user and context key`() {
        val cache = cache(config)
        val expected = remoteConfig(contextKey = "paywall", payloadValue = "user-a")
        cache.save(expected)

        assertNull(cache.get("onboarding"))

        config.uid = "user-b"
        assertNull(cache.get("paywall"))

        config.uid = "user-a"
        val otherProject = internalConfig(projectKey = "project-b", userId = "user-a")
        assertNull(cache(otherProject).get("paywall"))
        assertEquals(expected, cache.get("paywall"))
    }

    @Test
    fun `cache is isolated between production and sandbox environments`() {
        val productionConfig = internalConfig(
            projectKey = "project-a",
            userId = "user-a",
            environment = QEnvironment.Production,
        )
        val sandboxConfig = internalConfig(
            projectKey = "project-a",
            userId = "user-a",
            environment = QEnvironment.Sandbox,
        )
        val expected = remoteConfig(contextKey = "paywall", payloadValue = "production")

        cache(productionConfig).save(expected)

        assertNull(cache(sandboxConfig).get("paywall"))
        assertEquals(expected, cache(productionConfig).get("paywall"))
    }

    @Test
    fun `new server value replaces the prior context value`() {
        val cache = cache(config)
        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "v1"))

        val expected = remoteConfig(contextKey = "paywall", payloadValue = "v2")
        cache.save(expected)

        assertEquals(expected, cache.get("paywall"))
        assertEquals(listOf(expected), cache.getAll().remoteConfigs)
    }

    @Test
    fun `remove evicts only the authoritative missing context`() {
        val cache = cache(config)
        val retained = remoteConfig(contextKey = "onboarding", payloadValue = "retained")
        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "stale"))
        cache.save(retained)

        cache.remove("paywall")

        assertNull(cache.get("paywall"))
        assertEquals(retained, cache.get("onboarding"))
    }

    @Test
    fun `authoritative replacement evicts configs omitted by the server`() {
        val cache = cache(config)
        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "stale"))
        cache.save(remoteConfig(contextKey = "onboarding", payloadValue = "stale"))
        val current = remoteConfig(contextKey = "onboarding", payloadValue = "current")

        cache.replaceAll(listOf(current))

        assertNull(cache.get("paywall"))
        assertEquals(listOf(current), cache.getAll().remoteConfigs)

        cache.replaceAll(emptyList())

        assertTrue(cache.getAll().remoteConfigs.isEmpty())
        assertTrue(backingCache.strings.isEmpty())
    }

    @Test
    fun `filtered reconciliation persists one atomic snapshot visible after restart`() {
        val oldFirst = remoteConfig("first", "old-first")
        val omittedSecond = remoteConfig("second", "old-second")
        val unrelated = remoteConfig("unrelated", "unrelated")
        val cache = cache(config)
        cache.replaceAll(listOf(oldFirst, omittedSecond, unrelated))
        backingCache.batchUpdates.clear()
        val currentFirst = remoteConfig("first", "current-first")

        cache.replaceRequested(setOf("first", "second"), listOf(currentFirst))

        assertEquals(1, backingCache.batchUpdates.size)
        val restarted = cache(config)
        assertEquals(listOf(unrelated, currentFirst), restarted.getAll().remoteConfigs)
        assertNull(restarted.get("second"))
    }

    @Test
    fun `invalid config is not persisted`() {
        val cache = cache(config)

        cache.save(QRemoteConfig(payload = mapOf("value" to "invalid"), experiment = null, sourceApi = null))

        assertTrue(backingCache.strings.isEmpty())
    }

    @Test
    fun `corrupted cache is ignored and cleared`() {
        val cache = cache(config)
        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "v1"))
        val key = backingCache.strings.entries.single { it.value?.contains("\"remoteConfigs\"") == true }.key
        backingCache.strings[key] = "{not-json"

        assertNull(cache(config).get("paywall"))
        assertTrue(backingCache.strings.isEmpty())
    }

    @Test
    fun `unknown cache version is ignored and cleared`() {
        val cache = cache(config)
        cache.save(remoteConfig(contextKey = "paywall", payloadValue = "v1"))
        val key = backingCache.strings.entries.single { it.value?.contains("\"remoteConfigs\"") == true }.key
        backingCache.strings[key] = requireNotNull(backingCache.strings.getValue(key))
            .replace(Regex("\"version\":\\d+"), "\"version\":999")

        assertNull(cache(config).get("paywall"))
        assertTrue(backingCache.strings.isEmpty())
    }

    @Test
    fun `untrusted index key can never remove an unrelated preference`() {
        val unrelatedPreference = "customer_auth_token"
        backingCache.putString(unrelatedPreference, "must-survive")
        backingCache.putString(
            INDEX_KEY,
            indexJson(scopeJson(unrelatedPreference, 1)),
        )
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
        )

        limited.save(remoteConfig("ctx", "current"))

        assertEquals("must-survive", backingCache.getString(unrelatedPreference, null))
        assertFalse(requireNotNull(backingCache.getString(INDEX_KEY, null)).contains(unrelatedPreference))
    }

    @Test
    fun `invalid index metadata is discarded without removing referenced keys`() {
        val validA = storageKey('a')
        val validB = storageKey('b')
        val cases = listOf(
            "uppercase key" to IndexCase(
                scopes = listOf(scopeJson(storageKey('A'), 1)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "short key" to IndexCase(
                scopes = listOf(scopeJson("qonversion_remote_config_lkg_${"a".repeat(63)}", 1)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "zero bytes" to IndexCase(
                scopes = listOf(scopeJson(validA, 0)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "negative bytes" to IndexCase(
                scopes = listOf(scopeJson(validA, -1)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "oversized bytes" to IndexCase(
                scopes = listOf(scopeJson(validA, 10_001)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "duplicate keys" to IndexCase(
                scopes = listOf(scopeJson(validA, 1), scopeJson(validA, 1)),
                limits = RemoteConfigCacheLimits(maxScopes = 2, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "too many scopes" to IndexCase(
                scopes = listOf(scopeJson(validA, 1), scopeJson(validB, 1)),
                limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
            ),
            "overflowing total" to IndexCase(
                scopes = listOf(scopeJson(validA, Int.MAX_VALUE), scopeJson(validB, Int.MAX_VALUE)),
                limits = RemoteConfigCacheLimits(
                    maxScopes = 8,
                    maxEntriesPerScope = 4,
                    maxTotalBytes = Int.MAX_VALUE,
                ),
            ),
        )

        cases.forEach { (label, case) ->
            backingCache.strings.clear()
            backingCache.batchUpdates.clear()
            val referencedKeys = case.scopes.mapNotNull { scope ->
                Regex("\"storageKey\":\"([^\"]+)\"").find(scope)?.groupValues?.get(1)
            }.distinct()
            referencedKeys.forEach { backingCache.putString(it, "must-survive-$label") }
            backingCache.putString(INDEX_KEY, indexJson(*case.scopes.toTypedArray()))

            cache(config, limits = case.limits).save(remoteConfig("ctx-$label", "current"))

            referencedKeys.forEach { referencedKey ->
                assertEquals(
                    label,
                    "must-survive-$label",
                    backingCache.getString(referencedKey, null),
                )
            }
            val rebuiltIndex = requireNotNull(backingCache.getString(INDEX_KEY, null))
            referencedKeys.forEach { referencedKey ->
                assertFalse(label, rebuiltIndex.contains(referencedKey))
            }
        }
    }

    @Test
    fun `oversized raw index is rejected before it can name removal targets`() {
        val referencedKey = storageKey('c')
        backingCache.putString(referencedKey, "must-survive")
        backingCache.putString(
            INDEX_KEY,
            indexJson(scopeJson(referencedKey, 1)) + " ".repeat(70_000),
        )
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
        )

        limited.save(remoteConfig("ctx", "current"))

        assertEquals("must-survive", backingCache.getString(referencedKey, null))
        val rebuiltIndex = requireNotNull(backingCache.getString(INDEX_KEY, null))
        assertTrue(rebuiltIndex.toByteArray(Charsets.UTF_8).size < 70_000)
        assertFalse(rebuiltIndex.contains(referencedKey))
    }

    @Test
    fun `under-reported index bytes are discarded after checking the stored payload`() {
        cache(config).save(remoteConfig("first", "x".repeat(2_000)))
        val firstStorageKey = persistedEnvelopeKey()
        val firstPayloadBytes = persistedEnvelopeJson(firstStorageKey).utf8Size()
        backingCache.putString(INDEX_KEY, indexJson(scopeJson(firstStorageKey, 1)))
        backingCache.putString(UNRELATED_KEY, "must-survive")
        config.uid = "user-b"
        val restarted = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 4,
                maxTotalBytes = firstPayloadBytes,
            ),
        )

        restarted.save(remoteConfig("second", "small"))

        val rebuiltIndex = requireNotNull(backingCache.getString(INDEX_KEY, null))
        assertFalse(rebuiltIndex.contains(firstStorageKey))
        assertEquals("must-survive", backingCache.getString(UNRELATED_KEY, null))
    }

    @Test
    fun `oversized envelope is rejected after process restart`() {
        cache(config).save(remoteConfig("ctx", "x".repeat(2_000)))
        val storageKey = persistedEnvelopeKey()
        val payloadBytes = persistedEnvelopeJson(storageKey).utf8Size()
        backingCache.putString(UNRELATED_KEY, "must-survive")
        val restarted = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 4,
                maxTotalBytes = payloadBytes - 1,
            ),
        )

        assertNull(restarted.get("ctx"))
        assertNull(backingCache.getString(storageKey, null))
        assertEquals("must-survive", backingCache.getString(UNRELATED_KEY, null))
    }

    @Test
    fun `envelope with too many entries is rejected after process restart`() {
        cache(config).replaceAll(
            listOf(
                remoteConfig("first", "first"),
                remoteConfig("second", "second"),
                remoteConfig("third", "third"),
            ),
        )
        val storageKey = persistedEnvelopeKey()
        backingCache.putString(UNRELATED_KEY, "must-survive")
        val restarted = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 2,
                maxTotalBytes = 100_000,
            ),
        )

        assertTrue(restarted.getAll().remoteConfigs.isEmpty())
        assertNull(backingCache.getString(storageKey, null))
        assertEquals("must-survive", backingCache.getString(UNRELATED_KEY, null))
    }

    @Test
    fun `envelope with duplicate canonical context keys is rejected after process restart`() {
        cache(config).save(remoteConfig("seed", "seed"))
        val storageKey = persistedEnvelopeKey()
        val poisonedJson = envelopeJson(
            listOf(
                remoteConfig(null, "null-context"),
                remoteConfig("", "empty-context"),
            ),
        )
        backingCache.putString(storageKey, poisonedJson)
        backingCache.putString(INDEX_KEY, indexJson(scopeJson(storageKey, poisonedJson.utf8Size())))
        backingCache.putString(UNRELATED_KEY, "must-survive")

        assertNull(cache(config).get(null))
        assertNull(backingCache.getString(storageKey, null))
        assertEquals("must-survive", backingCache.getString(UNRELATED_KEY, null))
    }

    @Test
    fun `index entry whose envelope hashes to another scope is discarded`() {
        val forgedStorageKey = storageKey('d')
        val forgedJson = envelopeJson(listOf(remoteConfig("forged", "forged")))
        backingCache.putString(forgedStorageKey, forgedJson)
        backingCache.putString(INDEX_KEY, indexJson(scopeJson(forgedStorageKey, forgedJson.utf8Size())))
        backingCache.putString(UNRELATED_KEY, "must-survive")

        cache(config).save(remoteConfig("current", "current"))

        val rebuiltIndex = requireNotNull(backingCache.getString(INDEX_KEY, null))
        assertFalse(rebuiltIndex.contains(forgedStorageKey))
        assertEquals(forgedJson, backingCache.getString(forgedStorageKey, null))
        assertEquals("must-survive", backingCache.getString(UNRELATED_KEY, null))
    }

    @Test
    fun `least recently used identity scope is evicted when scope limit is reached`() {
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 2, maxEntriesPerScope = 10, maxTotalBytes = 100_000),
        )
        val first = remoteConfig(contextKey = "paywall", payloadValue = "first")
        val second = remoteConfig(contextKey = "paywall", payloadValue = "second")
        val third = remoteConfig(contextKey = "paywall", payloadValue = "third")

        config.uid = "user-a"
        limited.save(first)
        config.uid = "user-b"
        limited.save(second)
        config.uid = "user-a"
        assertEquals(first, limited.get("paywall"))
        config.uid = "user-c"
        limited.save(third)

        config.uid = "user-b"
        assertNull(limited.get("paywall"))
        config.uid = "user-a"
        assertEquals(first, limited.get("paywall"))
        config.uid = "user-c"
        assertEquals(third, limited.get("paywall"))
    }

    @Test
    fun `least recently used context is evicted when entry limit is reached`() {
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 2, maxEntriesPerScope = 2, maxTotalBytes = 100_000),
        )
        val first = remoteConfig(contextKey = "first", payloadValue = "first")
        val second = remoteConfig(contextKey = "second", payloadValue = "second")
        val third = remoteConfig(contextKey = "third", payloadValue = "third")

        limited.save(first)
        limited.save(second)
        assertEquals(first, limited.get("first"))
        limited.save(third)

        assertNull(limited.get("second"))
        assertEquals(first, limited.get("first"))
        assertEquals(third, limited.get("third"))
    }

    @Test
    fun `oversized scope is not retained in memory or on disk`() {
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 2, maxEntriesPerScope = 2, maxTotalBytes = 1),
        )

        limited.save(remoteConfig(contextKey = "paywall", payloadValue = "too-large"))

        assertNull(limited.get("paywall"))
        assertTrue(backingCache.strings.values.none { it?.contains("too-large") == true })
    }

    @Test
    fun `oversized replacement preserves prior valid config for the same context`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "small")
        cache(config).save(previous)
        val previousEnvelopeBytes = requireNotNull(
            backingCache.strings.values.single { it?.contains("\"remoteConfigs\"") == true },
        ).toByteArray(Charsets.UTF_8).size
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 2,
                maxTotalBytes = previousEnvelopeBytes + 16,
            ),
        )

        limited.save(remoteConfig(contextKey = "paywall", payloadValue = "x".repeat(previousEnvelopeBytes)))

        assertEquals(previous, limited.get("paywall"))
        assertEquals(previous, cache(config).get("paywall"))
    }

    @Test
    fun `byte limiting serializes one bounded newest suffix instead of every dropped prefix`() {
        val countingFactory = CountingEnvelopeAdapterFactory()
        val countingMoshi = Moshi.Builder()
            .add(countingFactory)
            .add(QRemoteConfigurationSourceTypeAdapter())
            .add(QRemoteConfigurationSourceAssignmentTypeAdapter())
            .build()
        val maxBytes = 16_000
        val limited = PersistentRemoteConfigCache(
            cache = backingCache,
            config = config,
            moshi = countingMoshi,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 64,
                maxTotalBytes = maxBytes,
            ),
            persistenceExecutor = directExecutor,
        )
        val configs = List(64) { index ->
            remoteConfig("context-$index", "$index-${"x".repeat(2_000)}")
        }

        limited.replaceAll(configs)

        assertTrue(countingFactory.envelopeWrites <= 2)
        val persisted = limited.getAll().remoteConfigs
        assertTrue(persisted.isNotEmpty())
        assertTrue(persisted.size < configs.size)
        assertEquals(configs.takeLast(persisted.size), persisted)
        val persistedBytes = backingCache.strings.values
            .filterNotNull()
            .single { it.contains("\"remoteConfigs\"") }
            .toByteArray(Charsets.UTF_8)
            .size
        assertTrue(persistedBytes <= maxBytes)
    }

    @Test
    fun `invalid authoritative replacement preserves the whole prior last known good set`() {
        val paywall = remoteConfig(contextKey = "paywall", payloadValue = "paywall")
        val onboarding = remoteConfig(contextKey = "onboarding", payloadValue = "onboarding")
        val cache = cache(config)
        cache.replaceAll(listOf(paywall, onboarding))
        val invalid = QRemoteConfig(payload = mapOf("value" to "invalid"), experiment = null, sourceApi = null)

        cache.replaceAll(listOf(paywall, invalid))

        assertEquals(listOf(paywall, onboarding), cache.getAll().remoteConfigs)
    }

    @Test
    fun `authoritative replacement with duplicate canonical keys preserves prior last known good`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "previous")
        val cache = cache(config)
        cache.replaceAll(listOf(previous))

        cache.replaceAll(
            listOf(
                remoteConfig(contextKey = null, payloadValue = "null-context"),
                remoteConfig(contextKey = "", payloadValue = "empty-context"),
            ),
        )

        assertEquals(listOf(previous), cache.getAll().remoteConfigs)
        assertEquals(listOf(previous), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `total payload bytes evict least recently used scope`() {
        val first = remoteConfig(contextKey = "paywall", payloadValue = "first")
        val second = remoteConfig(contextKey = "paywall", payloadValue = "second")
        cache(config).save(first)
        val oneEnvelopeBytes = requireNotNull(
            backingCache.strings.values.single { it?.contains("\"remoteConfigs\"") == true },
        ).toByteArray(Charsets.UTF_8).size
        backingCache.strings.clear()
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 10,
                maxEntriesPerScope = 10,
                maxTotalBytes = oneEnvelopeBytes + 16,
            ),
        )

        config.uid = "user-a"
        limited.save(first)
        config.uid = "user-b"
        limited.save(second)

        val persistedPayloadBytes = backingCache.strings.values
            .filterNotNull()
            .filter { it.contains("\"remoteConfigs\"") }
            .sumOf { it.toByteArray(Charsets.UTF_8).size }
        assertTrue(persistedPayloadBytes <= oneEnvelopeBytes + 16)
        config.uid = "user-a"
        assertNull(limited.get("paywall"))
        config.uid = "user-b"
        assertEquals(second, limited.get("paywall"))
    }

    @Test
    fun `durable save completion waits for the off-thread commit`() {
        val queuedExecutor = ManualExecutor()
        val asyncCache = cache(config, executor = queuedExecutor)
        val expected = remoteConfig(contextKey = "paywall", payloadValue = "v1")
        var committed: Boolean? = null

        asyncCache.save(requireNotNull(asyncCache.currentScope()), expected) { result ->
            committed = result
        }

        assertTrue(backingCache.strings.isEmpty())
        assertNull(committed)
        assertNull(asyncCache.get("paywall"))
        queuedExecutor.runAll()
        assertEquals(true, committed)
        assertEquals(expected, cache(config).get("paywall"))
    }

    @Test
    fun `failed durable save preserves the prior committed last known good`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "previous")
        val persistent = cache(config)
        persistent.save(previous)
        backingCache.nextDurableUpdateResult = false
        var committed: Boolean? = null

        persistent.save(
            requireNotNull(persistent.currentScope()),
            remoteConfig(contextKey = "paywall", payloadValue = "fresh"),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(previous, persistent.get("paywall"))
        assertEquals(previous, cache(config).get("paywall"))
    }

    @Test
    fun `failed first durable save never exposes the fresh value from memory or disk`() {
        val persistent = cache(config)
        backingCache.nextDurableUpdateResult = false
        var committed: Boolean? = null

        persistent.save(
            requireNotNull(persistent.currentScope()),
            remoteConfig(contextKey = "paywall", payloadValue = "fresh"),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertNull(persistent.get("paywall"))
        assertNull(cache(config).get("paywall"))
        assertTrue(backingCache.strings.isEmpty())
    }

    @Test
    fun `exception during durable save preserves the prior committed last known good`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "previous")
        val persistent = cache(config)
        persistent.save(previous)
        backingCache.throwOnNextDurableUpdate = true
        var committed: Boolean? = null

        persistent.save(
            requireNotNull(persistent.currentScope()),
            remoteConfig(contextKey = "paywall", payloadValue = "fresh"),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(previous, persistent.get("paywall"))
        assertEquals(previous, cache(config).get("paywall"))
    }

    @Test
    fun `rejected persistence scheduling fails completion without replacing prior LKG`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "previous")
        cache(config).save(previous)
        val rejectingCache = cache(
            config,
            executor = Executor { throw RejectedExecutionException("shutting down") },
        )
        var committed: Boolean? = null

        rejectingCache.save(
            requireNotNull(rejectingCache.currentScope()),
            remoteConfig(contextKey = "paywall", payloadValue = "fresh"),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(previous, rejectingCache.get("paywall"))
        assertEquals(previous, cache(config).get("paywall"))
    }

    @Test
    fun `failed durable removal preserves payload and index for restart`() {
        val previous = remoteConfig(contextKey = "paywall", payloadValue = "previous")
        val persistent = cache(config)
        persistent.save(previous)
        val priorStrings = backingCache.strings.toMap()
        backingCache.nextDurableUpdateResult = false
        var committed: Boolean? = null

        persistent.remove(requireNotNull(persistent.currentScope()), "paywall") { result ->
            committed = result
        }

        assertEquals(false, committed)
        assertEquals(priorStrings, backingCache.strings)
        assertEquals(previous, persistent.get("paywall"))
        assertEquals(previous, cache(config).get("paywall"))
    }

    @Test
    fun `failed durable list reconciliation preserves the whole prior snapshot`() {
        val first = remoteConfig(contextKey = "first", payloadValue = "old-first")
        val second = remoteConfig(contextKey = "second", payloadValue = "old-second")
        val persistent = cache(config)
        persistent.replaceAll(listOf(first, second))
        val priorStrings = backingCache.strings.toMap()
        backingCache.nextDurableUpdateResult = false
        var committed: Boolean? = null

        persistent.replaceRequested(
            requireNotNull(persistent.currentScope()),
            requestedContextKeys = setOf("first", "second"),
            remoteConfigs = listOf(remoteConfig("first", "fresh-first")),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(priorStrings, backingCache.strings)
        assertEquals(listOf(first, second), persistent.getAll().remoteConfigs)
        assertEquals(listOf(first, second), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced durable saves succeed when the latest snapshot contains both requested values`() {
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val first = remoteConfig(contextKey = "first", payloadValue = "first")
        val second = remoteConfig(contextKey = "second", payloadValue = "second")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(scope, first) { completions += "first" to it }
        persistent.save(scope, second) { completions += "second" to it }

        assertTrue(completions.isEmpty())
        queuedExecutor.runNext()
        assertTrue(completions.isEmpty())
        queuedExecutor.runNext()

        assertEquals(listOf("first" to true, "second" to true), completions)
        assertEquals(listOf(first, second), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced durable save reports a superseded value for the same context as uncommitted`() {
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val first = remoteConfig(contextKey = "shared", payloadValue = "first")
        val second = remoteConfig(contextKey = "shared", payloadValue = "second")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(scope, first) { completions += "first" to it }
        persistent.save(scope, second) { completions += "second" to it }

        queuedExecutor.runAll()

        assertEquals(listOf("first" to false, "second" to true), completions)
        assertEquals(listOf(second), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced removal and unrelated save both succeed when the target stays absent`() {
        val target = remoteConfig(contextKey = "target", payloadValue = "old")
        cache(config).save(target)
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val unrelated = remoteConfig(contextKey = "unrelated", payloadValue = "fresh")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.remove(scope, "target") { completions += "remove" to it }
        persistent.save(scope, unrelated) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("remove" to true, "save" to true), completions)
        assertEquals(listOf(unrelated), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced removal fails when a later save re-adds the same target`() {
        cache(config).save(remoteConfig(contextKey = "target", payloadValue = "old"))
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val replacement = remoteConfig(contextKey = "target", payloadValue = "fresh")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.remove(scope, "target") { completions += "remove" to it }
        persistent.save(scope, replacement) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("remove" to false, "save" to true), completions)
        assertEquals(listOf(replacement), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced requested replacement and unrelated save both succeed`() {
        val oldRequested = remoteConfig(contextKey = "requested", payloadValue = "old")
        cache(config).save(oldRequested)
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val replacement = remoteConfig(contextKey = "requested", payloadValue = "fresh")
        val unrelated = remoteConfig(contextKey = "unrelated", payloadValue = "fresh")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.replaceRequested(scope, setOf("requested"), listOf(replacement)) {
            completions += "replace" to it
        }
        persistent.save(scope, unrelated) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("replace" to true, "save" to true), completions)
        assertEquals(listOf(replacement, unrelated), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced requested replacement fails when a later save overwrites its target`() {
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val replacement = remoteConfig(contextKey = "requested", payloadValue = "first")
        val overwrite = remoteConfig(contextKey = "requested", payloadValue = "second")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.replaceRequested(scope, setOf("requested"), listOf(replacement)) {
            completions += "replace" to it
        }
        persistent.save(scope, overwrite) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("replace" to false, "save" to true), completions)
        assertEquals(listOf(overwrite), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced requested omission fails when a later save re-adds the omitted target`() {
        cache(config).save(remoteConfig(contextKey = "requested", payloadValue = "old"))
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val readded = remoteConfig(contextKey = "requested", payloadValue = "fresh")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.replaceRequested(scope, setOf("requested"), emptyList()) {
            completions += "replace" to it
        }
        persistent.save(scope, readded) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("replace" to false, "save" to true), completions)
        assertEquals(listOf(readded), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced requested omission and unrelated save both succeed while the omitted target stays absent`() {
        cache(config).save(remoteConfig(contextKey = "requested", payloadValue = "old"))
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val unrelated = remoteConfig(contextKey = "unrelated", payloadValue = "fresh")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.replaceRequested(scope, setOf("requested"), emptyList()) {
            completions += "replace" to it
        }
        persistent.save(scope, unrelated) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("replace" to true, "save" to true), completions)
        assertEquals(listOf(unrelated), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `coalesced replace all reports false when a later mutation changes its whole snapshot`() {
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val authoritative = remoteConfig(contextKey = "requested", payloadValue = "fresh")
        val later = remoteConfig(contextKey = "unrelated", payloadValue = "later")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.replaceAll(scope, listOf(authoritative)) { completions += "replaceAll" to it }
        persistent.save(scope, later) { completions += "save" to it }
        queuedExecutor.runAll()

        assertEquals(listOf("replaceAll" to false, "save" to true), completions)
        assertEquals(listOf(authoritative, later), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `oversized newer single save fails only itself and does not cancel an accepted pending save`() {
        val fitting = remoteConfig(contextKey = "fitting", payloadValue = "small")
        cache(config).save(fitting)
        val oneEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val queuedExecutor = ManualExecutor()
        val persistent = cache(
            config,
            limits = RemoteConfigCacheLimits(2, 4, oneEnvelopeBytes + 16),
            executor = queuedExecutor,
        )
        val oversized = remoteConfig("oversized", "x".repeat(oneEnvelopeBytes))
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(requireNotNull(persistent.currentScope()), fitting) {
            completions += "fitting" to it
        }
        persistent.save(requireNotNull(persistent.currentScope()), oversized) {
            completions += "oversized" to it
        }

        assertEquals(listOf("oversized" to false), completions)
        queuedExecutor.runAll()
        assertEquals(listOf("oversized" to false, "fitting" to true), completions)
        assertEquals(listOf(fitting), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `oversized newer strict replacement fails only itself and preserves an accepted pending replacement`() {
        val fitting = remoteConfig(contextKey = "fitting", payloadValue = "small")
        cache(config).save(fitting)
        val oneEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val queuedExecutor = ManualExecutor()
        val persistent = cache(
            config,
            limits = RemoteConfigCacheLimits(2, 4, oneEnvelopeBytes + 16),
            executor = queuedExecutor,
        )
        val oversized = remoteConfig("oversized", "x".repeat(oneEnvelopeBytes))
        val completions = mutableListOf<Pair<String, Boolean>>()
        val scope = requireNotNull(persistent.currentScope())

        persistent.replaceAll(scope, listOf(fitting)) { completions += "fitting" to it }
        persistent.replaceAll(scope, listOf(oversized)) { completions += "oversized" to it }

        assertEquals(listOf("oversized" to false), completions)
        queuedExecutor.runAll()
        assertEquals(listOf("oversized" to false, "fitting" to true), completions)
        assertEquals(listOf(fitting), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `rejected newer save restores an accepted pending save and completes each exactly once`() {
        val executor = AcceptFirstRejectSecondExecutor()
        val persistent = cache(config, executor = executor)
        val scope = requireNotNull(persistent.currentScope())
        val first = remoteConfig(contextKey = "first", payloadValue = "first")
        val second = remoteConfig(contextKey = "second", payloadValue = "second")
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(scope, first) { completions += "first" to it }
        persistent.save(scope, second) { completions += "second" to it }

        assertEquals(listOf("second" to false), completions)
        executor.runAccepted()
        assertEquals(listOf("second" to false, "first" to true), completions)
        assertEquals(listOf(first), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `executor that runs then rejects cannot complete the same durable save twice`() {
        val persistent = cache(config, executor = RunThenRejectExecutor())
        val expected = remoteConfig(contextKey = "context", payloadValue = "value")
        val completions = mutableListOf<Boolean>()

        persistent.save(requireNotNull(persistent.currentScope()), expected) {
            completions += it
        }

        assertEquals(listOf(true), completions)
        assertEquals(expected, cache(config).get("context"))
    }

    @Test
    fun `strict durable reconciliation never commits a bounded partial snapshot`() {
        val second = remoteConfig(contextKey = "second", payloadValue = "second-${"x".repeat(1_000)}")
        cache(config).save(second)
        val oneEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val strictCache = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 4,
                maxTotalBytes = oneEnvelopeBytes + 16,
            ),
        )
        val first = remoteConfig(contextKey = "first", payloadValue = "first-${"x".repeat(1_000)}")
        var committed: Boolean? = null

        strictCache.replaceAll(
            requireNotNull(strictCache.currentScope()),
            listOf(first, second),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertTrue(backingCache.strings.isEmpty())
        assertTrue(strictCache.getAll().remoteConfigs.isEmpty())
    }

    @Test
    fun `durable single save may evict old contexts when the requested value is fully committed`() {
        val old = remoteConfig(contextKey = "old", payloadValue = "old-${"x".repeat(1_000)}")
        cache(config).save(old)
        val oneEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val boundedCache = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 4,
                maxTotalBytes = oneEnvelopeBytes + 16,
            ),
        )
        boundedCache.save(old)
        val fresh = remoteConfig(contextKey = "fresh", payloadValue = "fresh-${"x".repeat(1_000)}")
        var committed: Boolean? = null

        boundedCache.save(requireNotNull(boundedCache.currentScope()), fresh) { result ->
            committed = result
        }

        assertEquals(true, committed)
        assertEquals(listOf(fresh), boundedCache.getAll().remoteConfigs)
        assertEquals(listOf(fresh), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `strict operation after a bounded pending save builds from the admitted snapshot`() {
        val oldFirst = remoteConfig(contextKey = "old-first", payloadValue = "first-${"x".repeat(1_000)}")
        val oldSecond = remoteConfig(contextKey = "old-second", payloadValue = "second-${"x".repeat(1_000)}")
        cache(config).replaceAll(listOf(oldFirst, oldSecond))
        val twoEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val limits = RemoteConfigCacheLimits(
            maxScopes = 2,
            maxEntriesPerScope = 4,
            maxTotalBytes = twoEnvelopeBytes + 16,
        )
        cache(config, limits = limits).replaceAll(listOf(oldFirst, oldSecond))
        val queuedExecutor = ManualExecutor()
        val persistent = cache(config, limits = limits, executor = queuedExecutor)
        val scope = requireNotNull(persistent.currentScope())
        val added = remoteConfig(contextKey = "added", payloadValue = "added-${"x".repeat(1_000)}")
        val updatedSecond = remoteConfig(
            contextKey = "old-second",
            payloadValue = "updated-${"x".repeat(1_000)}",
        )
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(scope, added) { completions += "save" to it }
        persistent.replaceRequested(scope, setOf("old-second"), listOf(updatedSecond)) {
            completions += "replace" to it
        }

        assertTrue(completions.isEmpty())
        queuedExecutor.runAll()
        assertEquals(listOf("save" to true, "replace" to true), completions)
        assertEquals(listOf(added, updatedSecond), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `rejection restores the admitted bounded snapshot for the next strict operation`() {
        val oldFirst = remoteConfig(contextKey = "old-first", payloadValue = "first-${"x".repeat(1_000)}")
        val oldSecond = remoteConfig(contextKey = "old-second", payloadValue = "second-${"x".repeat(1_000)}")
        cache(config).replaceAll(listOf(oldFirst, oldSecond))
        val twoEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        backingCache.strings.clear()
        backingCache.batchUpdates.clear()
        val limits = RemoteConfigCacheLimits(2, 4, twoEnvelopeBytes + 16)
        cache(config, limits = limits).replaceAll(listOf(oldFirst, oldSecond))
        val executor = ScriptedExecutor(acceptance = listOf(true, false, true))
        val persistent = cache(config, limits = limits, executor = executor)
        val scope = requireNotNull(persistent.currentScope())
        val added = remoteConfig(contextKey = "added", payloadValue = "added-${"x".repeat(1_000)}")
        val rejected = remoteConfig(
            contextKey = "rejected",
            payloadValue = "rejected-${"x".repeat(1_000)}",
        )
        val updatedSecond = remoteConfig(
            contextKey = "old-second",
            payloadValue = "updated-${"x".repeat(1_000)}",
        )
        val completions = mutableListOf<Pair<String, Boolean>>()

        persistent.save(scope, added) { completions += "save" to it }
        persistent.save(scope, rejected) { completions += "rejected" to it }
        persistent.replaceRequested(scope, setOf("old-second"), listOf(updatedSecond)) {
            completions += "replace" to it
        }

        assertEquals(listOf("rejected" to false), completions)
        executor.runAccepted()
        assertEquals(
            listOf("rejected" to false, "save" to true, "replace" to true),
            completions,
        )
        assertEquals(listOf(added, updatedSecond), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `durable single save rejects an individually oversized requested value without changing storage`() {
        val previous = remoteConfig(contextKey = "old", payloadValue = "small")
        cache(config).save(previous)
        val priorEnvelopeBytes = persistedEnvelopeJson(persistedEnvelopeKey()).utf8Size()
        val priorStrings = backingCache.strings.toMap()
        val boundedCache = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 4,
                maxTotalBytes = priorEnvelopeBytes + 16,
            ),
        )
        val oversized = remoteConfig(
            contextKey = "fresh",
            payloadValue = "oversized-${"x".repeat(priorEnvelopeBytes)}",
        )
        var committed: Boolean? = null

        boundedCache.save(requireNotNull(boundedCache.currentScope()), oversized) { result ->
            committed = result
        }

        assertEquals(false, committed)
        assertEquals(priorStrings, backingCache.strings)
        assertEquals(listOf(previous), boundedCache.getAll().remoteConfigs)
        assertEquals(listOf(previous), cache(config).getAll().remoteConfigs)
    }

    @Test
    fun `strict durable replace all rejects entry truncation without changing storage`() {
        val strictCache = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 1,
                maxTotalBytes = 10_000,
            ),
        )
        var committed: Boolean? = null

        strictCache.replaceAll(
            requireNotNull(strictCache.currentScope()),
            listOf(remoteConfig("first", "first"), remoteConfig("second", "second")),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertTrue(backingCache.strings.isEmpty())
        assertTrue(strictCache.getAll().remoteConfigs.isEmpty())
    }

    @Test
    fun `strict durable requested reconciliation rejects entry truncation and preserves prior snapshot`() {
        val first = remoteConfig("first", "first")
        val strictCache = cache(
            config,
            limits = RemoteConfigCacheLimits(
                maxScopes = 2,
                maxEntriesPerScope = 1,
                maxTotalBytes = 10_000,
            ),
        )
        strictCache.save(first)
        val priorStrings = backingCache.strings.toMap()
        var committed: Boolean? = null

        strictCache.replaceRequested(
            requireNotNull(strictCache.currentScope()),
            requestedContextKeys = setOf("second"),
            remoteConfigs = listOf(remoteConfig("second", "second")),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(priorStrings, backingCache.strings)
        assertEquals(listOf(first), strictCache.getAll().remoteConfigs)
    }

    @Test
    fun `failed scope eviction keeps payload and index on the prior atomic snapshot`() {
        val limited = cache(
            config,
            limits = RemoteConfigCacheLimits(maxScopes = 1, maxEntriesPerScope = 4, maxTotalBytes = 10_000),
        )
        val first = remoteConfig(contextKey = "ctx", payloadValue = "first")
        limited.save(first)
        val priorStrings = backingCache.strings.toMap()
        backingCache.nextDurableUpdateResult = false
        config.uid = "user-b"
        var committed: Boolean? = null

        limited.save(
            requireNotNull(limited.currentScope()),
            remoteConfig(contextKey = "ctx", payloadValue = "second"),
        ) { result -> committed = result }

        assertEquals(false, committed)
        assertEquals(priorStrings, backingCache.strings)
        config.uid = "user-a"
        assertEquals(first, limited.get("ctx"))
        config.uid = "user-b"
        assertNull(limited.get("ctx"))
    }

    private fun cache(
        internalConfig: InternalConfig,
        limits: RemoteConfigCacheLimits = RemoteConfigCacheLimits(),
        executor: Executor = directExecutor,
    ) = PersistentRemoteConfigCache(
        cache = backingCache,
        config = internalConfig,
        moshi = moshi,
        limits = limits,
        persistenceExecutor = executor,
    )

    private fun internalConfig(
        projectKey: String,
        userId: String,
        environment: QEnvironment = QEnvironment.Production,
    ) = InternalConfig(
        primaryConfig = PrimaryConfig(
            projectKey = projectKey,
            launchMode = QLaunchMode.SubscriptionManagement,
            environment = environment,
        ),
        cacheConfig = CacheConfig(QEntitlementsCacheLifetime.Month, null),
    ).also { it.uid = userId }

    private fun remoteConfig(contextKey: String?, payloadValue: String) = QRemoteConfig(
        payload = mapOf("value" to payloadValue),
        experiment = null,
        sourceApi = QRemoteConfigurationSource(
            id = "remote-config-id",
            name = "Remote Config",
            assignmentType = QRemoteConfigurationAssignmentType.Auto,
            type = QRemoteConfigurationSourceType.RemoteConfiguration,
            contextKeyApi = contextKey,
        ),
    )

    private fun persistedEnvelopeKey() = backingCache.strings.entries
        .single { it.value?.contains("\"remoteConfigs\"") == true }
        .key

    private fun persistedEnvelopeJson(storageKey: String) =
        requireNotNull(backingCache.getString(storageKey, null))

    private fun envelopeJson(remoteConfigs: List<QRemoteConfig>) = moshi
        .adapter(PersistentRemoteConfigEnvelope::class.java)
        .toJson(
            PersistentRemoteConfigEnvelope(
                version = 2,
                projectKey = config.primaryConfig.projectKey,
                environment = config.environment.name,
                userId = config.uid,
                remoteConfigs = remoteConfigs,
            ),
        )

    private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size

    private fun storageKey(hex: Char) = "qonversion_remote_config_lkg_${hex.toString().repeat(64)}"

    private fun scopeJson(storageKey: String, bytes: Int) =
        "{\"storageKey\":\"$storageKey\",\"bytes\":$bytes}"

    private fun indexJson(vararg scopes: String) =
        "{\"version\":1,\"scopes\":[${scopes.joinToString(",")}] }"

    private data class IndexCase(
        val scopes: List<String>,
        val limits: RemoteConfigCacheLimits,
    )

    private class InMemoryCache : Cache {
        data class BatchUpdate(
            val values: Map<String, String?>,
            val removedKeys: Set<String>,
        )

        val strings = mutableMapOf<String, String?>()
        val batchUpdates = mutableListOf<BatchUpdate>()
        var nextDurableUpdateResult = true
        var throwOnNextDurableUpdate = false
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
        override fun updateStrings(values: Map<String, String?>, removedKeys: Set<String>) {
            batchUpdates += BatchUpdate(values.toMap(), removedKeys.toSet())
            removedKeys.forEach(strings::remove)
            strings.putAll(values)
        }
        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
            if (throwOnNextDurableUpdate) {
                throwOnNextDurableUpdate = false
                throw IllegalStateException("simulated storage failure")
            }
            val result = nextDurableUpdateResult
            nextDurableUpdateResult = true
            if (result) updateStrings(values, removedKeys)
            return result
        }
        override fun getString(key: String, defValue: String?) = strings[key] ?: defValue
        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) {
            putString(key, adapter.toJson(value))
        }
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? =
            getString(key, null)?.let(adapter::fromJson)
        override fun remove(key: String) {
            strings.remove(key)
            values.remove(key)
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    private class AcceptFirstRejectSecondExecutor : Executor {
        private var accepted: Runnable? = null
        private var invocationCount = 0

        override fun execute(command: Runnable) {
            invocationCount += 1
            if (invocationCount == 1) {
                accepted = command
            } else {
                throw RejectedExecutionException("simulated rejection")
            }
        }

        fun runAccepted() {
            requireNotNull(accepted).run()
            accepted = null
        }
    }

    private class RunThenRejectExecutor : Executor {
        override fun execute(command: Runnable) {
            command.run()
            throw RejectedExecutionException("simulated rejection after execution")
        }
    }

    private class ScriptedExecutor(private val acceptance: List<Boolean>) : Executor {
        private val accepted = ArrayDeque<Runnable>()
        private var invocationCount = 0

        override fun execute(command: Runnable) {
            val accepts = acceptance.getOrElse(invocationCount) { false }
            invocationCount += 1
            if (accepts) {
                accepted.addLast(command)
            } else {
                throw RejectedExecutionException("simulated rejection")
            }
        }

        fun runAccepted() {
            while (accepted.isNotEmpty()) accepted.removeFirst().run()
        }
    }

    private class CountingEnvelopeAdapterFactory : JsonAdapter.Factory {
        var envelopeWrites = 0

        override fun create(
            type: Type,
            annotations: Set<Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            if (Types.getRawType(type) != PersistentRemoteConfigEnvelope::class.java) return null
            val delegate = moshi.nextAdapter<Any>(this, type, annotations)
            return object : JsonAdapter<Any>() {
                override fun fromJson(reader: JsonReader): Any? = delegate.fromJson(reader)

                override fun toJson(writer: JsonWriter, value: Any?) {
                    envelopeWrites += 1
                    delegate.toJson(writer, value)
                }
            }
        }
    }

    private companion object {
        const val INDEX_KEY = "qonversion_remote_config_lkg_index"
        const val UNRELATED_KEY = "customer_auth_token"
    }
}
