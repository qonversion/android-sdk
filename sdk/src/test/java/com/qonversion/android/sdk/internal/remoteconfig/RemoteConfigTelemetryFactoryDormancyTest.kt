@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigV2Config
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.storage.Cache
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigFetchCallback
import com.squareup.moshi.JsonAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.atomic.AtomicReference

private const val TELEMETRY_KEY_PREFIX = "qonversion_remote_config_v2_telemetry_"

/**
 * Telemetry is built by [RemoteConfigV2Factory] and therefore inherits the feature's single switch.
 *
 * Without a [QRemoteConfigV2Config] the factory builds no [RemoteConfigV2Manager] at all, and the
 * telemetry sender is reachable only through one — so "no manager" is not a proxy for dormancy, it
 * is dormancy. The behavioural assertions below pin the consequence: nothing is read from or
 * written to storage, and a read still answers.
 */
@RunWith(RobolectricTestRunner::class)
internal class RemoteConfigTelemetryFactoryDormancyTest {

    @Test
    fun `an unconfigured SDK builds no telemetry machinery at all`() {
        val cache = RecordingCache()

        val configs = RemoteConfigV2Factory.create(
            application = RuntimeEnvironment.getApplication(),
            internalConfig = internalConfig(remoteConfigV2Config = null),
            cache = cache,
            logger = SilentLogger(),
        )

        // No manager means no sender, no durable buffer, no timer and no queue.
        assertNull("a dormant SDK built the Remote Config v2 chain", configs.manager)
        assertEquals(QRemoteConfigFetchStatus.NotConfigured, fetchBlocking(configs).status)
        assertTrue("a dormant SDK read a value", configs.current.contextKeys.isEmpty())
        assertTrue(cache.touchedKeys.none { it.startsWith(TELEMETRY_KEY_PREFIX) })
        assertTrue(cache.durableWrites.isEmpty())
    }

    @Test
    fun `a configured SDK builds the chain but stays silent until an identity binds it`() {
        val cache = RecordingCache()

        val configs = RemoteConfigV2Factory.create(
            application = RuntimeEnvironment.getApplication(),
            internalConfig = internalConfig(
                remoteConfigV2Config = QRemoteConfigV2Config(
                    baseUrl = "https://rc.example.invalid/",
                    environmentUid = "production",
                ),
            ),
            cache = cache,
            logger = SilentLogger(),
        )

        // Without this the dormancy assertion above would also pass if the factory had stopped
        // building the chain entirely.
        assertNotNull(configs.manager)
        // Constructing the chain touches no telemetry storage: the buffer is only read when an
        // identity binds the sender.
        assertTrue(cache.touchedKeys.none { it.startsWith(TELEMETRY_KEY_PREFIX) })
        assertTrue(cache.durableWrites.none { it.startsWith(TELEMETRY_KEY_PREFIX) })
    }

    private fun fetchBlocking(configs: QRemoteConfigSnapshotsImpl): QRemoteConfigFetchResult {
        val result = AtomicReference<QRemoteConfigFetchResult>()
        configs.fetch(
            object : QonversionRemoteConfigFetchCallback {
                override fun onResult(result1: QRemoteConfigFetchResult) = result.set(result1)
            },
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return requireNotNull(result.get())
    }

    private fun internalConfig(remoteConfigV2Config: QRemoteConfigV2Config?) = InternalConfig(
        primaryConfig = PrimaryConfig(
            projectKey = "project-key",
            launchMode = QLaunchMode.SubscriptionManagement,
            environment = QEnvironment.Sandbox,
        ),
        cacheConfig = CacheConfig(
            entitlementsCacheLifetime = QEntitlementsCacheLifetime.Month,
            fallbackFileIdentifier = null,
        ),
        remoteConfigV2Config = remoteConfigV2Config,
    )

    private class RecordingCache : Cache {
        val touchedKeys = mutableListOf<String>()
        val durableWrites = mutableListOf<String>()

        override fun putInt(key: String, value: Int) = Unit
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getBool(key: String, defValue: Boolean): Boolean = defValue
        override fun putBool(key: String, value: Boolean) = Unit
        override fun putFloat(key: String, value: Float) = Unit
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun putLong(key: String, value: Long) = Unit
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun putString(key: String, value: String?) { touchedKeys += key }
        override fun remove(key: String) { touchedKeys += key }

        override fun getString(key: String, defValue: String?): String? {
            touchedKeys += key
            return defValue
        }

        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
            durableWrites += values.keys + removedKeys
            return true
        }

        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) = Unit
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? = null
    }
}
