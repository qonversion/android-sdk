@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal.remoteconfig

import android.content.pm.ApplicationInfo
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigV2Config
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.storage.Cache
import com.squareup.moshi.JsonAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The minimum-fetch-interval spec (Addendum B): the floor between real network fetches is
 * configurable by the app, and a debug build is unthrottled unless the app pins a value.
 *
 * The policy resolution is a pure function of the configuration and the build mode, so the
 * semantics are asserted on [RemoteConfigV2Factory.minimumFetchIntervalMillis] directly; the
 * Robolectric test below then pins that [RemoteConfigV2Factory.create] actually feeds the
 * application's own debuggable flag and the app's configuration into that function, so the two
 * halves cannot drift apart unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
internal class RemoteConfigV2FetchIntervalTest {

    @Test
    fun `auto resolves to the production default in a release build`() {
        assertEquals(60_000L, RemoteConfigV2Factory.minimumFetchIntervalMillis(config(), isDebuggable = false))
    }

    @Test
    fun `auto resolves to no throttling in a debug build`() {
        assertEquals(0L, RemoteConfigV2Factory.minimumFetchIntervalMillis(config(), isDebuggable = true))
    }

    @Test
    fun `an explicit interval wins over auto in both build modes`() {
        val explicit = config(minFetchIntervalSeconds = 300)

        assertEquals(300_000L, RemoteConfigV2Factory.minimumFetchIntervalMillis(explicit, isDebuggable = false))
        assertEquals(300_000L, RemoteConfigV2Factory.minimumFetchIntervalMillis(explicit, isDebuggable = true))
    }

    @Test
    fun `the factory feeds the application's debuggable flag into the built coordinator`() {
        val application = RuntimeEnvironment.getApplication()

        application.applicationInfo.flags = application.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        assertEquals(0L, builtIntervalMillis(config()))
        assertEquals(45_000L, builtIntervalMillis(config(minFetchIntervalSeconds = 45)))

        application.applicationInfo.flags = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        assertEquals(60_000L, builtIntervalMillis(config()))
        assertEquals(45_000L, builtIntervalMillis(config(minFetchIntervalSeconds = 45)))
    }

    /**
     * The interval the factory actually handed to the coordinator, read through the private chain.
     *
     * Reflection rather than a widened surface on purpose: the coordinator and its policy are
     * private to the manager by design, and opening them up for one assertion would trade a
     * test-only inconvenience for a production seam.
     */
    private fun builtIntervalMillis(config: QRemoteConfigV2Config): Long {
        val manager = requireNotNull(
            RemoteConfigV2Factory.create(
                application = RuntimeEnvironment.getApplication(),
                internalConfig = internalConfig(config),
                cache = NoOpCache(),
                logger = SilentLogger(),
            ).manager,
        )
        val coordinator = manager.readPrivate<RemoteConfigFetchCoordinator>("coordinator")
        return coordinator.readPrivate<RemoteConfigFetchPolicy>("policy").minimumFetchIntervalMillis
    }

    private inline fun <reified T> Any.readPrivate(name: String): T =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T

    private fun internalConfig(remoteConfigV2Config: QRemoteConfigV2Config) = InternalConfig(
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

    private fun config(minFetchIntervalSeconds: Long = 0) = QRemoteConfigV2Config(
        baseUrl = "https://rc.example.invalid/",
        environmentUid = "production",
        minFetchIntervalSeconds = minFetchIntervalSeconds,
    )

    private class NoOpCache : Cache {
        override fun putInt(key: String, value: Int) = Unit
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getBool(key: String, defValue: Boolean): Boolean = defValue
        override fun putBool(key: String, value: Boolean) = Unit
        override fun putFloat(key: String, value: Float) = Unit
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun putLong(key: String, value: Long) = Unit
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun putString(key: String, value: String?) = Unit
        override fun getString(key: String, defValue: String?): String? = defValue
        override fun remove(key: String) = Unit
        override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean = true
        override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) = Unit
        override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? = null
    }
}
