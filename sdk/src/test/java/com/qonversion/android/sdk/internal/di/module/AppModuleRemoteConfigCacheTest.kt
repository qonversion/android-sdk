package com.qonversion.android.sdk.internal.di.module

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigurationAssignmentType
import com.qonversion.android.sdk.dto.QRemoteConfigurationSource
import com.qonversion.android.sdk.dto.QRemoteConfigurationSourceType
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.storage.PersistentRemoteConfigCache
import com.qonversion.android.sdk.internal.storage.RemoteConfigCache
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the [AppModule.provideRemoteConfigCache] binding: the graph must serve the persistent
 * (v1 last-known-good) implementation, wired to the module's own [InternalConfig] for scoping and
 * to the shared preferences cache for storage — with a Moshi that can actually round-trip a
 * [QRemoteConfig], which is why the test uses the exact instance [NetworkModule.provideMoshi]
 * contributes to the graph.
 */
@RunWith(RobolectricTestRunner::class)
internal class AppModuleRemoteConfigCacheTest {

    private val internalConfig = internalConfig(userId = "user-a")
    private val module = AppModule(
        ApplicationProvider.getApplicationContext<Application>(),
        internalConfig,
        mockk<AppStateProvider>(),
    )
    private val moshi = NetworkModule().provideMoshi()
    private val prefsCache = module.provideSharedPreferencesCache(
        module.provideSharedPreferences(module.provideApplication()),
    )

    @Test
    fun `the graph serves a persistent cache that survives provider recreation`() {
        val cache = module.provideRemoteConfigCache(moshi, prefsCache)
        assertTrue(cache is PersistentRemoteConfigCache)

        val expected = remoteConfig(contextKey = "paywall", payloadValue = "v1")
        saveBlocking(cache, expected)

        // A fresh provider call — the next process, in DI terms — reads the same storage.
        val recreated = module.provideRemoteConfigCache(moshi, prefsCache)
        assertEquals(expected, recreated.get("paywall"))
        assertEquals(listOf(expected), recreated.getAll().remoteConfigs)
    }

    @Test
    fun `the cache is scoped by the identity of the module's own InternalConfig`() {
        val cache = module.provideRemoteConfigCache(moshi, prefsCache)
        saveBlocking(cache, remoteConfig(contextKey = "paywall", payloadValue = "user-a"))

        // The provider captured the module's config, not a copy: a uid change re-scopes reads.
        internalConfig.uid = "user-b"
        assertNull(cache.get("paywall"))

        internalConfig.uid = "user-a"
        assertEquals("user-a", cache.get("paywall")?.payload?.get("value"))
    }

    private fun saveBlocking(cache: RemoteConfigCache, remoteConfig: QRemoteConfig) {
        // The DI-provided cache persists on its own background executor; the completion overload
        // is the only way to know the write is durable before asserting on a fresh instance.
        val scope = requireNotNull(cache.currentScope()) { "the provided cache derives no scope" }
        val persisted = CountDownLatch(1)
        cache.save(scope, remoteConfig) { persisted.countDown() }
        assertTrue("the provided cache never completed its write", persisted.await(10, TimeUnit.SECONDS))
    }

    private fun internalConfig(userId: String) = InternalConfig(
        primaryConfig = PrimaryConfig(
            projectKey = "project-key",
            launchMode = QLaunchMode.SubscriptionManagement,
            environment = QEnvironment.Production,
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
}
