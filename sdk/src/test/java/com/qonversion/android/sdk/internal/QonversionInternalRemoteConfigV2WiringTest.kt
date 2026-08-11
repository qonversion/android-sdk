@file:OptIn(ExperimentalQonversionApi::class)

package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchResult
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigFetchStatus
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigV2Config
import com.qonversion.android.sdk.internal.di.QDependencyInjector
import com.qonversion.android.sdk.internal.di.component.AppComponent
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.remoteconfig.MainThreadDispatcher
import com.qonversion.android.sdk.internal.remoteconfig.QRemoteConfigSnapshotsImpl
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigFetchForceReason
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigIdentityBridge
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigV2Factory
import com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigV2Manager
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigFetchCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.atomic.AtomicReference

private const val INITIAL_UID = "initial-uid"

/**
 * Pins the init-path wiring between [QonversionInternal] and the Remote Config v2 subsystem.
 *
 * [com.qonversion.android.sdk.internal.remoteconfig.RemoteConfigTelemetryFactoryDormancyTest]
 * proves the factory itself builds nothing without a [QRemoteConfigV2Config]; this test pins the
 * consequence one level up: an unconfigured init leaves the v1 identity bridge unbound (so both
 * identity moments are safe no-ops), while a configured init force-fetches for the boot identity
 * and routes both bridge moments into the v2 manager, reading the live uid each time.
 */
@RunWith(RobolectricTestRunner::class)
internal class QonversionInternalRemoteConfigV2WiringTest {

    private val appComponent = mockk<AppComponent>(relaxed = true)
    private val remoteConfigManager = mockk<QRemoteConfigManager>(relaxed = true)
    private val identityBridge = RemoteConfigIdentityBridge()
    private val sharedPreferencesCache = mockk<SharedPreferencesCache>(relaxed = true)
    private val userInfoService = mockk<QUserInfoService>()

    @Before
    fun setUp() {
        mockkObject(QDependencyInjector)
        every { QDependencyInjector.buildAppComponent(any(), any(), any()) } returns appComponent
        every { QDependencyInjector.appComponent } returns appComponent
        every { appComponent.remoteConfigManager() } returns remoteConfigManager
        every { remoteConfigManager.identityBridge } returns identityBridge
        every { appComponent.sharedPreferencesCache() } returns sharedPreferencesCache
        every { appComponent.userInfoService() } returns userInfoService
        every { userInfoService.obtainUserId() } returns INITIAL_UID

        // The real product center chain builds a Play Billing client and fires a launch request
        // during init; both are irrelevant to the v2 wiring under test.
        mockkConstructor(QonversionFactory::class)
        every {
            anyConstructed<QonversionFactory>().createProductCenterManager(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `an unconfigured init leaves the identity bridge unbound and identity events no-op`() {
        val internalConfig = internalConfig(remoteConfigV2Config = null)

        val qonversion = QonversionInternal(internalConfig, RuntimeEnvironment.getApplication())

        // The real factory ran and built only the bundled-defaults facade.
        val snapshots = qonversion.remoteConfigSnapshots() as QRemoteConfigSnapshotsImpl
        assertNull("a dormant init built the Remote Config v2 chain", snapshots.manager)
        assertEquals(QRemoteConfigFetchStatus.NotConfigured, fetchBlocking(snapshots).status)

        // Neither v1 identity moment is routed anywhere: the callbacks stay null, and firing the
        // bridge — exactly what QRemoteConfigManager does on logout/identify — is a safe no-op.
        assertNull(identityBridge.onIdentityScopeChanged)
        assertNull(identityBridge.onTargetingInvalidated)
        identityBridge.identityScopeChanged()
        identityBridge.targetingInvalidated()

        // Dormancy is also storage silence: no v2 key is ever read or written.
        verify(exactly = 0) { sharedPreferencesCache.getString(match { it.contains("remote_config_v2") }, any()) }
        verify(exactly = 0) { sharedPreferencesCache.putString(match { it.contains("remote_config_v2") }, any()) }
        verify(exactly = 0) {
            sharedPreferencesCache.updateStringsDurably(
                match { values -> values.keys.any { it.contains("remote_config_v2") } },
                any(),
            )
        }
    }

    @Test
    fun `a configured init force-fetches for the boot identity and bridges both identity moments`() {
        val manager = configuredManager()
        val internalConfig = internalConfig(remoteConfigV2Config = v2Config())

        val qonversion = QonversionInternal(internalConfig, RuntimeEnvironment.getApplication())

        // The factory was handed the same config and cache the rest of the SDK uses...
        verify(exactly = 1) {
            RemoteConfigV2Factory.create(
                RuntimeEnvironment.getApplication(),
                internalConfig,
                sharedPreferencesCache,
                any(),
            )
        }
        // ...its facade is what the public accessor serves...
        assertSame(manager, (qonversion.remoteConfigSnapshots() as QRemoteConfigSnapshotsImpl).manager)
        // ...and init bound the uid learned from storage with a forced Build fetch, exactly once.
        verify(exactly = 1) { manager.updateIdentity(INITIAL_UID, RemoteConfigFetchForceReason.Build) }

        // A v1 identity transition switches the v2 scope with a forced Identify fetch.
        identityBridge.identityScopeChanged()
        verify(exactly = 1) { manager.updateIdentity(INITIAL_UID, RemoteConfigFetchForceReason.Identify) }

        // The bridge reads the live uid at event time, not the one captured during init.
        internalConfig.uid = "next-uid"
        identityBridge.identityScopeChanged()
        verify(exactly = 1) { manager.updateIdentity("next-uid", RemoteConfigFetchForceReason.Identify) }

        // A targeting invalidation re-reads targeting without a scope transition.
        identityBridge.targetingInvalidated()
        verify(exactly = 1) { manager.refreshTargeting() }
    }

    @Test
    fun `a throwing v2 manager never breaks the v1 identity moments`() {
        val manager = configuredManager()
        every {
            manager.updateIdentity(any(), RemoteConfigFetchForceReason.Identify)
        } throws IllegalStateException("v2 refused the identity change")
        every { manager.refreshTargeting() } throws IllegalStateException("v2 refused the refresh")

        QonversionInternal(internalConfig(remoteConfigV2Config = v2Config()), RuntimeEnvironment.getApplication())

        // QRemoteConfigManager fires these on the v1 identity path; an optional subsystem that
        // throws must never propagate into it.
        identityBridge.identityScopeChanged()
        identityBridge.targetingInvalidated()
    }

    private fun configuredManager(): RemoteConfigV2Manager {
        val manager = mockk<RemoteConfigV2Manager>(relaxed = true)
        mockkObject(RemoteConfigV2Factory)
        every { RemoteConfigV2Factory.create(any(), any(), any(), any()) } returns
            QRemoteConfigSnapshotsImpl(manager, { null }, MainThreadDispatcher())
        return manager
    }

    private fun v2Config() = QRemoteConfigV2Config(
        baseUrl = "https://rc.example.invalid/",
        environmentUid = "production",
    )

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
}
