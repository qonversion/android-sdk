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
    fun `serialization is queued off caller thread while memory value is immediately available`() {
        val queuedExecutor = ManualExecutor()
        val asyncCache = cache(config, executor = queuedExecutor)
        val expected = remoteConfig(contextKey = "paywall", payloadValue = "v1")

        asyncCache.save(expected)

        assertTrue(backingCache.strings.isEmpty())
        assertEquals(expected, asyncCache.get("paywall"))
        queuedExecutor.runAll()
        assertEquals(expected, cache(config).get("paywall"))
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
