package com.qonversion.android.sdk.internal

import android.os.Build
import android.os.Looper
import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.internal.storage.RemoteConfigCache
import com.qonversion.android.sdk.internal.storage.RemoteConfigCacheScope
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import com.qonversion.android.sdk.listeners.QonversionEmptyCallback
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
internal class QRemoteConfigManagerTest {
    private val mockRemoteConfigService = mockk<QRemoteConfigService>(relaxed = true)
    private val mockFallbacksService = mockk<QFallbacksService>(relaxed = true)
    private val userStateProvider = FakeUserStateProvider()
    private val mockUserPropertiesManager = mockk<QUserPropertiesManager>(relaxed = true)
    private lateinit var persistentCache: FakeRemoteConfigCache

    private lateinit var manager: QRemoteConfigManager

    @Before
    fun setUp() {
        clearAllMocks()

        persistentCache = FakeRemoteConfigCache()
        manager = QRemoteConfigManager(mockRemoteConfigService, mockFallbacksService, persistentCache)
        manager.userStateProvider = userStateProvider
        manager.userPropertiesManager = mockUserPropertiesManager
    }

    @Test
    fun `successful server response is persisted as last known good`() {
        userStateProvider.stable = true
        val serverConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(serverConfig)

        assertEquals(serverConfig, persistentCache.get("ctx"))
        assertEquals(QRemoteConfigDeliveryOrigin.Network, manager.lastDeliveryOrigin("ctx"))
        verify(exactly = 1) { callback.onSuccess(serverConfig) }
    }

    @Test
    fun `single network success waits until its last known good is durably committed`() {
        userStateProvider.stable = true
        persistentCache.deferDurableMutations = true
        val serverConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(serverConfig)

        verify { callback wasNot Called }
        assertEquals(null, persistentCache.get("ctx"))

        persistentCache.completeNextDurableMutation(success = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(serverConfig, persistentCache.get("ctx"))
        verify(exactly = 1) { callback.onSuccess(serverConfig) }
    }

    @Test
    fun `failed single persistence serves the prior committed last known good instead of fresh data`() {
        userStateProvider.stable = true
        val previous = remoteConfigFor("ctx")
        val fresh = remoteConfigFor("ctx")
        persistentCache.save(previous)
        persistentCache.deferDurableMutations = true
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(fresh)
        persistentCache.completeNextDurableMutation(success = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(previous, persistentCache.get("ctx"))
        verify(exactly = 1) { callback.onSuccess(previous) }
        verify(exactly = 0) { callback.onSuccess(fresh) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun `failed single persistence without fallback reports an error instead of fresh unsaved data`() {
        userStateProvider.stable = true
        persistentCache.deferDurableMutations = true
        val fresh = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockFallbacksService.obtainFallbackData() } returns null
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(fresh)
        persistentCache.completeNextDurableMutation(success = false)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) {
            callback.onError(match { it.code == QonversionErrorCode.ResponseParsingFailed })
        }
    }

    @Test
    fun `invalidation while persistence is pending reissues and never delivers the superseded response`() {
        userStateProvider.stable = true
        persistentCache.deferDurableMutations = true
        val superseded = remoteConfigFor("ctx")
        val fresh = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.first().onSuccess(superseded)
        manager.invalidateRemoteConfigsCache()
        persistentCache.completeNextDurableMutation(success = true)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { callback.onSuccess(any()) }
        assertEquals(2, serviceCallbacks.size)

        serviceCallbacks.last().onSuccess(fresh)
        persistentCache.completeNextDurableMutation(success = true)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { callback.onSuccess(fresh) }
        verify(exactly = 0) { callback.onSuccess(superseded) }
    }

    @Test
    fun `list network success waits for atomic durable reconciliation`() {
        userStateProvider.stable = true
        persistentCache.deferDurableMutations = true
        val fresh = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(fresh)))

        verify { callback wasNot Called }
        persistentCache.completeNextDurableMutation(success = true)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(fresh) }) }
    }

    @Test
    fun `failed list reconciliation preserves and serves the prior atomic snapshot`() {
        userStateProvider.stable = true
        val previous = remoteConfigFor("ctx")
        val fresh = remoteConfigFor("ctx")
        persistentCache.save(previous)
        persistentCache.deferDurableMutations = true
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(fresh)))
        persistentCache.completeNextDurableMutation(success = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(previous), persistentCache.getAll().remoteConfigs)
        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(previous) }) }
        verify(exactly = 0) { callback.onSuccess(match { fresh in it.remoteConfigs }) }
    }

    @Test
    fun `empty single context is canonicalized to the null context`() {
        userStateProvider.stable = true
        val callbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("", capture(callbacks)) } just runs
        every { mockRemoteConfigService.loadRemoteConfig(null, capture(callbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)

        manager.loadRemoteConfig("", callback)
        shadowOf(Looper.getMainLooper()).idle()
        callbacks.single().onSuccess(remoteConfigFor(null))

        verify(exactly = 1) { mockRemoteConfigService.loadRemoteConfig(null, any()) }
        verify(exactly = 0) { mockRemoteConfigService.loadRemoteConfig("", any()) }
        verify(exactly = 1) { callback.onSuccess(any()) }
        assertTrue(loadingStates().containsKey(null))
        assertEquals(false, loadingStates().containsKey(""))
        assertNotNull(persistentCache.get(null))
    }

    @Test
    fun `single response for a different context is rejected without poisoning last known good`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("requested")
        val poisonedResponse = remoteConfigFor("unexpected")
        persistentCache.save(lastKnownGood)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("requested", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("requested", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(poisonedResponse)

        verify(exactly = 1) { callback.onSuccess(lastKnownGood) }
        verify(exactly = 0) { callback.onSuccess(poisonedResponse) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(lastKnownGood, persistentCache.get("requested"))
        assertEquals(null, persistentCache.get("unexpected"))
        assertEquals(QRemoteConfigDeliveryOrigin.PersistentLastKnownGood, manager.lastDeliveryOrigin("requested"))
    }

    @Test
    fun `offline load after process restart serves persistent last known good before bundle`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("ctx")
        val bundledFallback = remoteConfigFor("ctx")
        persistentCache.save(lastKnownGood)
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(bundledFallback)),
        )
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        verify(exactly = 1) { callback.onSuccess(lastKnownGood) }
        verify(exactly = 0) { callback.onSuccess(bundledFallback) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
        assertEquals(QRemoteConfigDeliveryOrigin.PersistentLastKnownGood, manager.lastDeliveryOrigin("ctx"))
    }

    @Test
    fun `same identity invalidation forces network then degrades to persistent last known good`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("ctx")
        val callbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(callbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        callbacks.single().onSuccess(lastKnownGood)
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()

        val afterInvalidation = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", afterInvalidation)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, callbacks.size)
        callbacks.last().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        verify(exactly = 1) { afterInvalidation.onSuccess(lastKnownGood) }
        verify(exactly = 0) { afterInvalidation.onError(any()) }
    }

    @Test
    fun `authoritative single no-config evicts stale last known good`() {
        userStateProvider.stable = true
        val stale = remoteConfigFor("ctx")
        persistentCache.save(stale)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        val noConfig = QonversionError(QonversionErrorCode.RemoteConfigurationNotAvailable)
        serviceCallback.captured.onError(noConfig)

        assertEquals(null, persistentCache.get("ctx"))
        verify(exactly = 1) { callback.onError(noConfig) }
        verify(exactly = 0) { callback.onSuccess(stale) }
    }

    @Test
    fun `failed authoritative removal preserves prior LKG and reports persistence failure`() {
        userStateProvider.stable = true
        val stale = remoteConfigFor("ctx")
        persistentCache.save(stale)
        persistentCache.deferDurableMutations = true
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        val noConfig = QonversionError(QonversionErrorCode.RemoteConfigurationNotAvailable)
        serviceCallback.captured.onError(noConfig)

        verify { callback wasNot Called }
        assertEquals(stale, persistentCache.get("ctx"))

        persistentCache.completeNextDurableMutation(success = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(stale, persistentCache.get("ctx"))
        verify(exactly = 0) { callback.onError(noConfig) }
        verify(exactly = 1) {
            callback.onError(match { it.code == QonversionErrorCode.ResponseParsingFailed })
        }
        verify(exactly = 0) { callback.onSuccess(any()) }
    }

    @Test
    fun `invalidation before authoritative no-config response fences the stale removal`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("ctx")
        persistentCache.save(lastKnownGood)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.invalidateRemoteConfigsCache()
        serviceCallbacks.first().onError(
            QonversionError(QonversionErrorCode.RemoteConfigurationNotAvailable),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(lastKnownGood, persistentCache.get("ctx"))
        assertEquals(2, serviceCallbacks.size)
        verify { callback wasNot Called }
    }

    @Test
    fun `bundled fallback is never persisted as last known good`() {
        userStateProvider.stable = true
        val bundledFallback = remoteConfigFor("ctx")
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(bundledFallback)),
        )
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        assertTrue(persistentCache.savedConfigs.isEmpty())
        assertEquals(QRemoteConfigDeliveryOrigin.BundledFallback, manager.lastDeliveryOrigin("ctx"))
    }

    @Test
    fun `successful server list response persists every config`() {
        userStateProvider.stable = true
        val first = remoteConfigFor("first")
        val second = remoteConfigFor("second")
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("first", "second"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("first", "second"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(first, second)))

        assertEquals(listOf(first, second), persistentCache.getAll().remoteConfigs)
    }

    @Test
    fun `empty named contexts are filtered before a scoped list request`() {
        userStateProvider.stable = true
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(any<List<String>>(), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("", "ctx", ""), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        val response = remoteConfigFor("ctx")
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(response)))

        verify(exactly = 1) {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, any())
        }
        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(response) }) }
        assertEquals(false, loadingStates().containsKey(""))
    }

    @Test
    fun `filtered list reconciliation is one persistent cache mutation`() {
        userStateProvider.stable = true
        val oldFirst = remoteConfigFor("first")
        val omittedSecond = remoteConfigFor("second")
        val unrelated = remoteConfigFor("unrelated")
        persistentCache.save(oldFirst)
        persistentCache.save(omittedSecond)
        persistentCache.save(unrelated)
        persistentCache.mutationCount = 0
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("first", "second"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("first", "second"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        val currentFirst = remoteConfigFor("first")
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(currentFirst)))

        assertEquals(1, persistentCache.mutationCount)
        assertEquals(currentFirst, persistentCache.get("first"))
        assertEquals(null, persistentCache.get("second"))
        assertEquals(unrelated, persistentCache.get("unrelated"))
    }

    @Test
    fun `scoped list rejects unexpected context without mutating last known good`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("requested")
        val unexpected = remoteConfigFor("unexpected")
        persistentCache.save(lastKnownGood)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("requested"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("requested"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(unexpected)))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(lastKnownGood) }) }
        verify(exactly = 0) { callback.onSuccess(match { unexpected in it.remoteConfigs }) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(lastKnownGood, persistentCache.get("requested"))
        assertEquals(null, persistentCache.get("unexpected"))
    }

    @Test
    fun `scoped list rejects duplicate contexts as one malformed response`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("requested")
        val duplicateA = remoteConfigFor("requested")
        val duplicateB = remoteConfigFor("requested")
        persistentCache.save(lastKnownGood)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("requested"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("requested"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(duplicateA, duplicateB)))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(lastKnownGood) }) }
        verify(exactly = 0) { callback.onSuccess(match { duplicateA in it.remoteConfigs || duplicateB in it.remoteConfigs }) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(lastKnownGood, persistentCache.get("requested"))
    }

    @Test
    fun `all-context list rejects duplicate contexts and preserves the previous set`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("previous")
        val duplicateA = remoteConfigFor("duplicate")
        val duplicateB = remoteConfigFor("duplicate")
        persistentCache.save(lastKnownGood)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every { mockRemoteConfigService.loadRemoteConfigs(capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(duplicateA, duplicateB)))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(lastKnownGood) }) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(listOf(lastKnownGood), persistentCache.getAll().remoteConfigs)
    }

    @Test
    fun `requested server list omission evicts only the omitted requested context`() {
        userStateProvider.stable = true
        val staleRequested = remoteConfigFor("requested")
        val unrelated = remoteConfigFor("unrelated")
        persistentCache.save(staleRequested)
        persistentCache.save(unrelated)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("requested"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("requested"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(emptyList()))

        assertEquals(null, persistentCache.get("requested"))
        assertEquals(unrelated, persistentCache.get("unrelated"))
        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs.isEmpty() }) }
    }

    @Test
    fun `all-context server list atomically replaces stale last known good set`() {
        userStateProvider.stable = true
        val stale = remoteConfigFor("stale")
        val previousCurrent = remoteConfigFor("current")
        val current = remoteConfigFor("current")
        persistentCache.save(stale)
        persistentCache.save(previousCurrent)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every { mockRemoteConfigService.loadRemoteConfigs(capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(QRemoteConfigList(listOf(current)))

        assertEquals(listOf(current), persistentCache.getAll().remoteConfigs)
        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(current) }) }
    }

    @Test
    fun `user switch mid-flight reissues list and never delivers prior identity config`() {
        userStateProvider.stable = true
        val priorIdentityConfig = remoteConfigFor("ctx")
        val currentIdentityConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallbacks))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.first().onSuccess(QRemoteConfigList(listOf(priorIdentityConfig)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, serviceCallbacks.size)
        verify(exactly = 0) { callback.onSuccess(match { priorIdentityConfig in it.remoteConfigs }) }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        serviceCallbacks.last().onSuccess(QRemoteConfigList(listOf(currentIdentityConfig)))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(currentIdentityConfig) }) }
        assertEquals(listOf(currentIdentityConfig), persistentCache.savedConfigs)
    }

    @Test
    fun `user switch mid-flight reissues a failed list before consulting persistent fallback`() {
        userStateProvider.stable = true
        val priorIdentityConfig = remoteConfigFor("ctx")
        val currentIdentityConfig = remoteConfigFor("ctx")
        persistentCache.save(priorIdentityConfig)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallbacks))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        persistentCache.savedConfigs.clear()
        persistentCache.save(currentIdentityConfig)
        serviceCallbacks.first().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, serviceCallbacks.size)
        verify { callback wasNot Called }

        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(currentIdentityConfig) }) }
        verify(exactly = 0) { callback.onSuccess(match { priorIdentityConfig in it.remoteConfigs }) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun `non-recoverable error from a reissued list is not masked by persistent fallback`() {
        userStateProvider.stable = true
        val stale = remoteConfigFor("ctx")
        persistentCache.save(stale)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallbacks))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.first().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))
        shadowOf(Looper.getMainLooper()).idle()
        val authError = QonversionError(QonversionErrorCode.InvalidCredentials, httpCode = 401)
        serviceCallbacks.last().onError(authError)

        verify(exactly = 1) { callback.onError(authError) }
        verify(exactly = 0) { callback.onSuccess(match { stale in it.remoteConfigs }) }
    }

    @Test
    fun `offline requested list fills cache misses from bundle but persistent values win`() {
        userStateProvider.stable = true
        val lastKnownGood = remoteConfigFor("first")
        val bundledForSameKey = remoteConfigFor("first")
        val bundledForMissingKey = remoteConfigFor("second")
        persistentCache.save(lastKnownGood)
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(bundledForSameKey, bundledForMissingKey)),
        )
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("first", "second"), false, capture(serviceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("first", "second"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        verify(exactly = 1) {
            callback.onSuccess(match { it.remoteConfigs == listOf(lastKnownGood, bundledForMissingKey) })
        }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(QRemoteConfigDeliveryOrigin.PersistentLastKnownGood, manager.lastDeliveryOrigin("first"))
        assertEquals(QRemoteConfigDeliveryOrigin.BundledFallback, manager.lastDeliveryOrigin("second"))
    }

    @Test
    fun `offline all-context list serves persistent values before bundled list`() {
        userStateProvider.stable = true
        val first = remoteConfigFor("first")
        val second = remoteConfigFor("second")
        persistentCache.save(first)
        persistentCache.save(second)
        val bundled = remoteConfigFor("bundled")
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(bundled)),
        )
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigListCallback>()
        every { mockRemoteConfigService.loadRemoteConfigs(capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(callback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(first, second, bundled) }) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun `loadRemoteConfigList from a background thread defers the listRequests mutation to the main thread`() {
        // given - the user is not stable, so loadRemoteConfigList enqueues the request
        userStateProvider.stable = false
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)

        // when - invoked from a non-main thread
        val backgroundThread = Thread { manager.loadRemoteConfigList(callback) }
        backgroundThread.start()
        backgroundThread.join()

        // then - the mutation has not happened yet: it was posted to the main looper
        // instead of being applied on the background thread (this is what kills the race)
        assertEquals(0, listRequests().size)

        // and once the main looper runs, the request is recorded on the main thread
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, listRequests().size)
    }

    @Test
    fun `loadRemoteConfig from a background thread defers the loadingStates mutation to the main thread`() {
        // given - the user is not stable, so loadRemoteConfig registers a new loading state
        userStateProvider.stable = false

        // when - invoked from a non-main thread with a not-yet-cached context key
        val backgroundThread = Thread { manager.loadRemoteConfig("ctx", null) }
        backgroundThread.start()
        backgroundThread.join()

        // then - the map mutation was posted to the main looper, not applied on the
        // background thread
        assertEquals(0, loadingStates().size)

        // and once the main looper runs, the loading state is registered on the main thread
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, loadingStates().size)
    }

    @Test
    fun `handlePendingRequests does not throw when a handled request re-enqueues itself`() {
        // given - three pending list requests queued while the user was not stable
        userStateProvider.stable = false
        repeat(3) { manager.loadRemoteConfigList(mockk(relaxed = true)) }
        assertEquals(3, listRequests().size)

        // when - handling them while the user is still not stable makes each one re-enqueue.
        // Iterating a snapshot must keep this from throwing ConcurrentModificationException.
        manager.handlePendingRequests()

        // then - the originals were cleared and re-enqueued for the next launch
        assertEquals(3, listRequests().size)
    }

    @Test
    fun `handlePendingRequests clears the pending list requests after handling them`() {
        // given - two pending list requests queued while the user was not stable
        userStateProvider.stable = false
        manager.loadRemoteConfigList(mockk(relaxed = true))
        manager.loadRemoteConfigList(listOf("ctx"), false, mockk(relaxed = true))
        assertEquals(2, listRequests().size)

        // when - the user becomes stable and the pending requests are handled
        userStateProvider.stable = true
        manager.handlePendingRequests()

        // then - nothing stale lingers to be re-issued on the next launch
        assertEquals(0, listRequests().size)
    }

    @Test
    fun `concurrent loadRemoteConfigList and handlePendingRequests do not throw`() {
        // Reproduces SUP3-188: a caller thread adds to listRequests while the main thread
        // iterates it in handlePendingRequests. Main-thread confinement serialises both
        // onto the main thread, so the ConcurrentModificationException can no longer occur.
        userStateProvider.stable = false
        // Hand-written no-op callback keeps the concurrent hot loop free of mockk proxies.
        val callback = object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {}
            override fun onError(error: QonversionError) {}
        }
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val iterations = 1_000

        val adder = Thread {
            try {
                repeat(iterations) { manager.loadRemoteConfigList(callback) }
            } catch (t: Throwable) {
                errors.add(t)
            }
        }

        val mainLooper = shadowOf(Looper.getMainLooper())
        adder.start()
        try {
            repeat(iterations) {
                manager.handlePendingRequests()
                mainLooper.idle()
            }
        } catch (t: Throwable) {
            errors.add(t)
        }
        adder.join()
        mainLooper.idle()

        assertTrue("Concurrent access threw: $errors", errors.isEmpty())
    }

    @Test
    fun `concurrent loadRemoteConfig and userChangingRequestFailedWithError do not throw`() {
        // Guards the loadingStates MAP race: userChangingRequestFailedWithError iterates
        // loadingStates.keys on the main thread while loadRemoteConfig registers brand-new
        // keys. Confinement serialises both onto the main thread, so the structural
        // modification can no longer collide with the iteration.
        userStateProvider.stable = false
        val error = QonversionError(QonversionErrorCode.Unknown)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val iterations = 1_000

        val adder = Thread {
            try {
                repeat(iterations) { i -> manager.loadRemoteConfig("ctx_$i", null) }
            } catch (t: Throwable) {
                errors.add(t)
            }
        }

        val mainLooper = shadowOf(Looper.getMainLooper())
        adder.start()
        try {
            repeat(iterations) {
                manager.userChangingRequestFailedWithError(error)
                mainLooper.idle()
            }
        } catch (t: Throwable) {
            errors.add(t)
        }
        adder.join()
        mainLooper.idle()

        assertTrue("Concurrent access threw: $errors", errors.isEmpty())
    }

    @Test
    fun `userChangingRequestFailedWithError does not throw when a callback re-enters loadRemoteConfig`() {
        // Reentrant variant of SUP3-188. userChangingRequestFailedWithError iterates
        // loadingStates.keys on the main thread and fires user callbacks. If a callback
        // synchronously calls loadRemoteConfig for a brand-new key, that load runs inline
        // (already on the main thread) and registers the key in loadingStates - a structural
        // modification mid-iteration. Main-thread confinement cannot prevent this; only
        // iterating a snapshot (keys.toList()) does.
        userStateProvider.stable = false
        val error = QonversionError(QonversionErrorCode.Unknown)

        val reentrantCallback = object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) = Unit
            override fun onError(error: QonversionError) {
                // re-enter with a not-yet-seen key, adding to loadingStates mid-iteration
                manager.loadRemoteConfig("reentrant_key", null)
            }
        }
        val noopCallback = object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) = Unit
            override fun onError(error: QonversionError) = Unit
        }
        // Two pre-existing keys: the structural add happens while processing the first, and
        // the iterator must still advance to the second - which is where a live-keySet
        // iteration would trip ConcurrentModificationException.
        loadingStates()["ctx0"] = QRemoteConfigManager.LoadingState(callbacks = mutableListOf(reentrantCallback))
        loadingStates()["ctx1"] = QRemoteConfigManager.LoadingState(callbacks = mutableListOf(noopCallback))

        // when - failing all in-flight requests on the main thread
        manager.userChangingRequestFailedWithError(error)
        shadowOf(Looper.getMainLooper()).idle()

        // then - no ConcurrentModificationException, and the reentrant load registered its key
        assertTrue(loadingStates().containsKey("reentrant_key"))
    }

    @Test
    fun `loadRemoteConfig returns a cached config synchronously on the main thread`() {
        // given - a previously loaded config for the empty context key, user is stable
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()[null] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)

        // when - called on the test (main) thread, with no looper draining in between
        manager.loadRemoteConfig(null, callback)

        // then - the cached config is delivered synchronously, without hitting
        // the service, and pending properties are still flushed (a cache hit
        // must not swallow the flush - parity with iOS)
        verify(exactly = 1) { callback.onSuccess(cachedConfig) }
        verify { mockRemoteConfigService wasNot Called }
        verify(exactly = 1) { mockUserPropertiesManager.forceSendProperties(any()) }
    }

    @Test
    fun `a cache hit drains queued waiters instead of serving only the direct callback`() {
        // given - a warm state that still carries queued callbacks (e.g. a
        // list load cached into a state whose own load never fired them)
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        val queuedCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(
            loadedConfig = cachedConfig,
            callbacks = mutableListOf(queuedCallback),
        )
        val directCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)

        // when
        manager.loadRemoteConfig("ctx", directCallback)

        // then - both the direct caller and the stranded waiter are served
        verify(exactly = 1) { directCallback.onSuccess(cachedConfig) }
        verify(exactly = 1) { queuedCallback.onSuccess(cachedConfig) }
        assertEquals(0, loadingStates()["ctx"]?.callbacks?.size)
    }

    @Test
    fun `loadRemoteConfigList returns cached configs synchronously on the main thread`() {
        // given - a cached config for the requested context key, user is stable
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)

        // when - called on the test (main) thread, with no looper draining in between
        manager.loadRemoteConfigList(listOf("ctx"), false, callback)

        // then - the cached list is delivered synchronously, without hitting
        // the service, and pending properties are still flushed (a cache hit
        // must not swallow the flush - parity with iOS)
        verify(exactly = 1) { callback.onSuccess(any()) }
        verify { mockRemoteConfigService wasNot Called }
        verify(exactly = 1) { mockUserPropertiesManager.forceSendProperties(any()) }
        assertEquals(QRemoteConfigDeliveryOrigin.MemoryCache, manager.lastDeliveryOrigin("ctx"))
    }

    @Test
    fun `loadRemoteConfigList cache hit waits for stable identity before serving or flushing`() {
        // given - cached configs, but the user is mid-identify. The stability
        // gate exists so the flush cannot POST to a switching uid.
        userStateProvider.stable = false
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)

        // when
        manager.loadRemoteConfigList(listOf("ctx"), false, callback)

        // then - neither stale memory nor a properties request can cross the
        // identity boundary. The request is retained for replay after identify.
        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 0) { mockUserPropertiesManager.forceSendProperties(any()) }
        assertEquals(1, listRequests().size)
    }

    @Test
    fun `queued single waiter survives identity state reset and replays exactly once`() {
        userStateProvider.stable = false
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        manager.onUserUpdate {
            persistentCache.scope = persistentCache.scope.copy(userId = "user-b")
        }
        shadowOf(Looper.getMainLooper()).idle()

        userStateProvider.stable = true
        manager.handlePendingRequests()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, serviceCallbacks.size)

        val currentConfig = remoteConfigFor("ctx")
        serviceCallbacks.single().onSuccess(currentConfig)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { callback.onSuccess(currentConfig) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun `single preflight completion while identity is unstable defers request and waiter`() {
        userStateProvider.stable = true
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val propertyCallbacks = mutableListOf<QonversionEmptyCallback>()
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockUserPropertiesManager.forceSendProperties(capture(propertyCallbacks)) } just runs
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs

        manager.loadRemoteConfig("ctx", callback)
        userStateProvider.stable = false
        propertyCallbacks.single().onComplete()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(serviceCallbacks.isEmpty())
        verify { callback wasNot Called }

        userStateProvider.stable = true
        manager.handlePendingRequests()
        propertyCallbacks.last().onComplete()
        shadowOf(Looper.getMainLooper()).idle()
        val currentConfig = remoteConfigFor("ctx")
        serviceCallbacks.single().onSuccess(currentConfig)

        verify(exactly = 1) { callback.onSuccess(currentConfig) }
        verify(exactly = 0) { callback.onError(any()) }
    }

    @Test
    fun `single response while identity is unstable is reissued and delivered exactly once`() {
        userStateProvider.stable = true
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        userStateProvider.stable = false
        val unstableConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(unstableConfig)
        shadowOf(Looper.getMainLooper()).idle()

        verify { callback wasNot Called }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        userStateProvider.stable = true
        manager.handlePendingRequests()
        shadowOf(Looper.getMainLooper()).idle()
        val currentConfig = remoteConfigFor("ctx")
        serviceCallbacks.last().onSuccess(currentConfig)

        verify(exactly = 0) { callback.onSuccess(unstableConfig) }
        verify(exactly = 1) { callback.onSuccess(currentConfig) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(listOf(currentConfig), persistentCache.savedConfigs)
    }

    @Test
    fun `list preflight and response both wait for stable identity`() {
        userStateProvider.stable = true
        val preflightCallback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val preflightPropertyCallbacks = mutableListOf<QonversionEmptyCallback>()
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigListCallback>()
        every { mockUserPropertiesManager.forceSendProperties(capture(preflightPropertyCallbacks)) } just runs
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallbacks))
        } just runs

        manager.loadRemoteConfigList(listOf("ctx"), false, preflightCallback)
        userStateProvider.stable = false
        preflightPropertyCallbacks.single().onComplete()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(serviceCallbacks.isEmpty())
        verify { preflightCallback wasNot Called }

        userStateProvider.stable = true
        manager.handlePendingRequests()
        preflightPropertyCallbacks.last().onComplete()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, serviceCallbacks.size)

        userStateProvider.stable = false
        val unstableConfig = remoteConfigFor("ctx")
        serviceCallbacks.single().onSuccess(QRemoteConfigList(listOf(unstableConfig)))
        shadowOf(Looper.getMainLooper()).idle()

        verify { preflightCallback wasNot Called }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        userStateProvider.stable = true
        manager.handlePendingRequests()
        preflightPropertyCallbacks.last().onComplete()
        shadowOf(Looper.getMainLooper()).idle()
        val currentConfig = remoteConfigFor("ctx")
        serviceCallbacks.last().onSuccess(QRemoteConfigList(listOf(currentConfig)))

        verify(exactly = 1) {
            preflightCallback.onSuccess(match { it.remoteConfigs == listOf(currentConfig) })
        }
        verify(exactly = 0) { preflightCallback.onError(any()) }
        assertEquals(listOf(currentConfig), persistentCache.savedConfigs)
    }

    @Test
    fun `every invalidation entry point drops named-key cached configs non-destructively`() {
        // given - attach/detach are addressed by entity id only, so the SDK
        // cannot know which context key is served and must drop every cache;
        // the public invalidateRemoteConfigsCache shares the same semantics
        userStateProvider.stable = true
        val entryPoints = listOf<Pair<String, (QRemoteConfigManager) -> Unit>>(
            "attachUserToRemoteConfiguration" to { it.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true)) },
            "detachUserFromRemoteConfiguration" to { it.detachUserFromRemoteConfiguration("config_id", mockk(relaxed = true)) },
            "attachUserToExperiment" to { it.attachUserToExperiment("experiment_id", "group_id", mockk(relaxed = true)) },
            "detachUserFromExperiment" to { it.detachUserFromExperiment("experiment_id", mockk(relaxed = true)) },
            "invalidateRemoteConfigsCache" to { it.invalidateRemoteConfigsCache() },
        )

        entryPoints.forEach { (name, entryPoint) ->
            // given - cached configs under the empty AND a named context key,
            // plus a pending callback that must survive the invalidation
            val pendingCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
            loadingStates()[null] = QRemoteConfigManager.LoadingState(loadedConfig = mockk<QRemoteConfig>(relaxed = true))
            loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(
                loadedConfig = mockk<QRemoteConfig>(relaxed = true),
                callbacks = mutableListOf(pendingCallback),
            )

            // when
            entryPoint(manager)
            shadowOf(Looper.getMainLooper()).idle()

            // then - every cached config is dropped, but loading states and
            // their pending callbacks are preserved (non-destructive invalidation).
            // Assert on the states themselves first: a `?.` chain against a
            // missing key would make the null comparisons pass vacuously.
            val emptyKeyState = loadingStates()[null]
            val namedKeyState = loadingStates()["ctx"]
            assertNotNull("$name must preserve the empty-key loading state", emptyKeyState)
            assertNotNull("$name must preserve the named-key loading state", namedKeyState)
            assertEquals("$name must drop the empty-key config", null, emptyKeyState?.loadedConfig)
            assertEquals("$name must drop the named-key config", null, namedKeyState?.loadedConfig)
            assertEquals("$name must preserve pending callbacks", 1, namedKeyState?.callbacks?.size)
        }
    }

    @Test
    fun `invalidation mid-flight re-issues the load instead of delivering the stale response`() {
        // given - a load with a waiting callback is in flight when the
        // invalidation lands
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the cache is invalidated mid-flight, then the superseded
        // response lands
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val staleConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(staleConfig)

        // then - the stale evaluation is neither cached nor delivered; the
        // load is re-issued exactly once for the waiting callback
        verify(exactly = 0) { loadCallback.onSuccess(any()) }
        verify(exactly = 2) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }

        // and the fresh response is delivered, cached, and the state settled
        val freshConfig = remoteConfigFor("ctx")
        serviceCallbacks.last().onSuccess(freshConfig)
        verify(exactly = 1) { loadCallback.onSuccess(freshConfig) }
        verify(exactly = 0) { loadCallback.onSuccess(staleConfig) }
        val state = loadingStates()["ctx"]
        assertNotNull(state)
        assertEquals(freshConfig, state?.loadedConfig)
        assertEquals(false, state?.isInProgress)
    }

    @Test
    fun `attach invalidation mid-flight also re-issues an awaited load`() {
        // given - a load with a waiting callback is in flight when the attach
        // lands (attach shares the invalidation seam with the public API)
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        manager.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val staleConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(staleConfig)

        // then - the pre-attach evaluation is dropped and the load re-issued
        verify(exactly = 0) { loadCallback.onSuccess(any()) }
        verify(exactly = 2) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }
    }

    @Test
    fun `a list load warming a superseded single-key state does not strand its waiters`() {
        // given - a single-key load with a waiter is in flight
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        val listServiceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(listServiceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - an invalidation lands, then a list load started AFTER it
        // caches the same key, and only then the superseded single-key
        // response arrives, so its re-issue hits the warm cache
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        manager.loadRemoteConfigList(listOf("ctx"), false, mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val warmConfig = mockk<QRemoteConfig>(relaxed = true)
        every { warmConfig.source.contextKey } returns "ctx"
        listServiceCallback.captured.onSuccess(QRemoteConfigList(listOf(warmConfig)))
        serviceCallbacks.first().onSuccess(remoteConfigFor("ctx"))

        // then - the waiter is served exactly once with the warm (current
        // generation) config instead of hanging forever, and no second
        // single-key request was needed
        verify(exactly = 1) { loadCallback.onSuccess(warmConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
        verify(exactly = 1) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }
        assertEquals(0, loadingStates()["ctx"]?.callbacks?.size)
    }

    @Test
    fun `a failed re-issue delivers the superseded evaluation instead of an error`() {
        // given - a load with a waiter is in flight
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - invalidation mid-flight, the superseded (valid) response
        // triggers a re-issue, and the retry fails transiently
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(supersededConfig)
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        // then - never worse than before: the superseded evaluation is
        // delivered as a success instead of surfacing the retry error, and
        // nothing is cached
        verify(exactly = 1) { loadCallback.onSuccess(supersededConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
    }

    @Test
    fun `a non-recoverable re-issue error is not masked by the superseded evaluation`() {
        userStateProvider.stable = true
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()

        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(supersededConfig)
        val authError = QonversionError(QonversionErrorCode.InvalidCredentials, httpCode = 401)
        serviceCallbacks.last().onError(authError)

        verify(exactly = 1) { callback.onError(authError) }
        verify(exactly = 0) { callback.onSuccess(supersededConfig) }
        assertEquals(null, manager.lastDeliveryOrigin("ctx"))
    }

    @Test
    fun `authoritative no-config during re-issue evicts disk and is not masked by baseline`() {
        userStateProvider.stable = true
        val stale = remoteConfigFor("ctx")
        persistentCache.save(stale)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()

        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(supersededConfig)
        val noConfig = QonversionError(QonversionErrorCode.RemoteConfigurationNotAvailable)
        serviceCallbacks.last().onError(noConfig)

        verify(exactly = 1) { callback.onError(noConfig) }
        verify(exactly = 0) { callback.onSuccess(any()) }
        assertEquals(null, persistentCache.get("ctx"))
    }

    @Test
    fun `a failed re-issue prefers the baseline over the bundled fallback`() {
        // given - a bundled fallback EXISTS for the key, and a load with a
        // waiter is in flight
        userStateProvider.stable = true
        val bundledConfig = mockk<QRemoteConfig>(relaxed = true)
        every { bundledConfig.source.contextKey } returns "ctx"
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(bundledConfig)),
        )
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - invalidation mid-flight, the superseded (valid) response
        // triggers a re-issue, and the retry fails in a FALLBACK-ELIGIBLE way
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(supersededConfig)
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        // then - the real user-specific evaluation seconds old outranks the
        // static bundled payload: the waiter gets the baseline, not the bundle
        verify(exactly = 1) { loadCallback.onSuccess(supersededConfig) }
        verify(exactly = 0) { loadCallback.onSuccess(bundledConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
    }

    @Test
    fun `a late joiner during the retry window also receives the baseline`() {
        // given - a load with a waiter is in flight
        userStateProvider.stable = true
        val callbackA = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", callbackA)
        shadowOf(Looper.getMainLooper()).idle()

        // when - invalidation mid-flight, the superseded response triggers a
        // re-issue, a SECOND caller joins while the retry is flying, and the
        // retry fails transiently
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(supersededConfig)
        val callbackB = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", callbackB)
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        // then - the never-worse guarantee is uniform: the late joiner gets
        // the baseline too, not the retry error
        verify(exactly = 1) { callbackA.onSuccess(supersededConfig) }
        verify(exactly = 1) { callbackB.onSuccess(supersededConfig) }
        verify(exactly = 0) { callbackA.onError(any()) }
        verify(exactly = 0) { callbackB.onError(any()) }
    }

    @Test
    fun `a stash consumed by a successful retry cannot resurface on a later failure`() {
        // given - a full re-issue cycle that ends with a SUCCESSFUL retry
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.first().onSuccess(remoteConfigFor("ctx"))
        serviceCallbacks.last().onSuccess(remoteConfigFor("ctx"))

        // when - a later, unrelated load for the same key fails
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val lateCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", lateCallback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.BackendError))

        // then - the error surfaces; a leftover stash must not deliver a
        // long-superseded evaluation as a success
        verify(exactly = 1) { lateCallback.onError(any()) }
        verify(exactly = 0) { lateCallback.onSuccess(any()) }
    }

    @Test
    fun `a stash consumed by a warm-cache resolution cannot resurface on a later failure`() {
        // given - the re-issue resolves through a warm cache (a list load
        // cached the key at the new generation)
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        val listServiceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(listServiceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        manager.loadRemoteConfigList(listOf("ctx"), false, mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val warmConfig = mockk<QRemoteConfig>(relaxed = true)
        every { warmConfig.source.contextKey } returns "ctx"
        listServiceCallback.captured.onSuccess(QRemoteConfigList(listOf(warmConfig)))
        serviceCallbacks.first().onSuccess(remoteConfigFor("ctx"))
        verify(exactly = 1) { loadCallback.onSuccess(warmConfig) }

        // when - a later, unrelated load for the same key fails
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val lateCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", lateCallback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.BackendError))

        // then - the error surfaces, not the stashed baseline
        verify(exactly = 1) { lateCallback.onError(any()) }
        verify(exactly = 0) { lateCallback.onSuccess(any()) }
    }

    @Test
    fun `a stash left by a user-change failure cannot resurface on a later failure`() {
        // given - the superseded response arrives while the user is unstable,
        // so the re-issue can only queue, and the identity change then fails -
        // the one drain path that bypasses the response handlers
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        userStateProvider.stable = false
        serviceCallbacks.first().onSuccess(remoteConfigFor("ctx"))
        manager.userChangingRequestFailedWithError(QonversionError(QonversionErrorCode.BackendError))
        shadowOf(Looper.getMainLooper()).idle()

        // when - the user stabilises and a later load for the same key fails
        userStateProvider.stable = true
        val lateCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", lateCallback)
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.BackendError))

        // then - the error surfaces, not the stashed baseline
        verify(exactly = 1) { lateCallback.onError(any()) }
        verify(exactly = 0) { lateCallback.onSuccess(any()) }
    }

    @Test
    fun `a reused callback instance is delivered exactly once on a cache hit`() {
        // given - a warm state whose queue already holds the same listener
        // instance the caller passes again (singleton-callback integrations)
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(
            loadedConfig = cachedConfig,
            callbacks = mutableListOf(callback),
        )

        // when
        manager.loadRemoteConfig("ctx", callback)

        // then - one delivery, not two
        verify(exactly = 1) { callback.onSuccess(cachedConfig) }
    }

    @Test
    fun `a user switch mid-flight reissues an awaited single load`() {
        // given - a load with a waiter is in flight
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the user switches (map replaced), then the response lands on
        // the now-orphaned state
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        val priorIdentityConfig = remoteConfigFor("ctx")
        serviceCallbacks.first().onSuccess(priorIdentityConfig)
        shadowOf(Looper.getMainLooper()).idle()

        // then - the old identity result is dropped and the original waiter is
        // carried into a request evaluated for the current identity.
        verify(exactly = 2) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }
        verify(exactly = 0) { loadCallback.onSuccess(priorIdentityConfig) }

        val currentIdentityConfig = remoteConfigFor("ctx")
        serviceCallbacks.last().onSuccess(currentIdentityConfig)

        verify(exactly = 1) { loadCallback.onSuccess(currentIdentityConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
    }

    @Test
    fun `old identity single success never resolves a new identity request`() {
        userStateProvider.stable = true
        val oldIdentityConfig = remoteConfigFor("ctx")
        val currentIdentityConfig = remoteConfigFor("ctx")
        val oldCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val currentCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", oldCallback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        manager.loadRemoteConfig("ctx", currentCallback)
        shadowOf(Looper.getMainLooper()).idle()

        serviceCallbacks.first().onSuccess(oldIdentityConfig)
        shadowOf(Looper.getMainLooper()).idle()

        verify { currentCallback wasNot Called }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        serviceCallbacks.last().onSuccess(currentIdentityConfig)

        verify(exactly = 1) { oldCallback.onSuccess(currentIdentityConfig) }
        verify(exactly = 0) { oldCallback.onSuccess(oldIdentityConfig) }
        verify(exactly = 0) { oldCallback.onError(any()) }
        verify(exactly = 1) { currentCallback.onSuccess(currentIdentityConfig) }
        verify(exactly = 0) { currentCallback.onSuccess(oldIdentityConfig) }
        assertEquals(listOf(currentIdentityConfig), persistentCache.savedConfigs)
    }

    @Test
    fun `old identity single error never resolves a new identity request`() {
        userStateProvider.stable = true
        val currentIdentityLkg = remoteConfigFor("ctx")
        val currentIdentityServerConfig = remoteConfigFor("ctx")
        val oldCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val currentCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", oldCallback)
        shadowOf(Looper.getMainLooper()).idle()
        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        persistentCache.save(currentIdentityLkg)
        manager.loadRemoteConfig("ctx", currentCallback)
        shadowOf(Looper.getMainLooper()).idle()

        serviceCallbacks.first().onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))
        shadowOf(Looper.getMainLooper()).idle()

        verify { currentCallback wasNot Called }

        serviceCallbacks.last().onSuccess(currentIdentityServerConfig)

        verify(exactly = 1) { oldCallback.onSuccess(currentIdentityServerConfig) }
        verify(exactly = 0) { oldCallback.onSuccess(currentIdentityLkg) }
        verify(exactly = 0) { oldCallback.onError(any()) }
        verify(exactly = 1) { currentCallback.onSuccess(currentIdentityServerConfig) }
        verify(exactly = 0) { currentCallback.onSuccess(currentIdentityLkg) }
    }

    @Test
    fun `background identity transition reissues with current scope and never saves or delivers old identity`() {
        userStateProvider.stable = true
        val oldConfig = remoteConfigFor("ctx")
        val currentConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        val requestUsers = mutableListOf<String>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } answers {
            requestUsers += persistentCache.scope.userId
        }
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        val transition = Thread {
            manager.onUserUpdate {
                persistentCache.scope = persistentCache.scope.copy(userId = "user-b")
            }
        }
        transition.start()
        transition.join()

        serviceCallbacks.first().onSuccess(oldConfig)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("user-a", "user-b"), requestUsers)
        verify { callback wasNot Called }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        serviceCallbacks.last().onSuccess(currentConfig)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { callback.onSuccess(currentConfig) }
        verify(exactly = 0) { callback.onSuccess(oldConfig) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(listOf("user-b"), persistentCache.savedScopes.map { it.userId })
    }

    @Test
    fun `main load immediately after background identity transition cannot join old identity state`() {
        userStateProvider.stable = true
        val oldCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val currentCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        val requestUsers = mutableListOf<String>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } answers {
            requestUsers += persistentCache.scope.userId
        }
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", oldCallback)
        shadowOf(Looper.getMainLooper()).idle()
        val transition = Thread {
            manager.onUserUpdate {
                persistentCache.scope = persistentCache.scope.copy(userId = "user-b")
            }
        }
        transition.start()
        transition.join()

        // The transition is already complete even though its housekeeping
        // runnable has not drained. This load must create current-user state,
        // while the old request's waiter is transferred into that state rather
        // than stranded on the orphaned user-a state.
        manager.loadRemoteConfig("ctx", currentCallback)

        assertEquals(listOf("user-a", "user-b"), requestUsers)
        val currentConfig = remoteConfigFor("ctx")
        serviceCallbacks.last().onSuccess(currentConfig)
        verify(exactly = 1) { currentCallback.onSuccess(currentConfig) }
        verify(exactly = 1) { oldCallback.onSuccess(currentConfig) }
        verify(exactly = 0) { oldCallback.onError(any()) }
    }

    @Test
    fun `identity transition during property flush only requests saves and delivers current user`() {
        userStateProvider.stable = true
        val currentConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val propertyCallbacks = mutableListOf<QonversionEmptyCallback>()
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        val requestUsers = mutableListOf<String>()
        every { mockUserPropertiesManager.forceSendProperties(capture(propertyCallbacks)) } just runs
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } answers {
            requestUsers += persistentCache.scope.userId
        }

        manager.loadRemoteConfig("ctx", callback)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, propertyCallbacks.size)
        assertTrue(serviceCallbacks.isEmpty())

        val transition = Thread {
            manager.onUserUpdate {
                persistentCache.scope = persistentCache.scope.copy(userId = "user-b")
            }
        }
        transition.start()
        transition.join()

        propertyCallbacks.first().onComplete()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, propertyCallbacks.size)
        assertTrue(serviceCallbacks.isEmpty())

        propertyCallbacks.last().onComplete()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("user-b"), requestUsers)

        serviceCallbacks.single().onSuccess(currentConfig)

        verify(exactly = 1) { callback.onSuccess(currentConfig) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(listOf(currentConfig), persistentCache.savedConfigs)
        assertEquals(listOf("user-b"), persistentCache.savedScopes.map { it.userId })
    }

    @Test
    fun `background identity transition reissues list in current scope`() {
        userStateProvider.stable = true
        val oldConfig = remoteConfigFor("ctx")
        val currentConfig = remoteConfigFor("ctx")
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigListCallback>()
        val requestUsers = mutableListOf<String>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(serviceCallbacks))
        } answers {
            requestUsers += persistentCache.scope.userId
        }
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfigList(listOf("ctx"), false, callback)
        shadowOf(Looper.getMainLooper()).idle()
        val transition = Thread {
            manager.onUserUpdate {
                persistentCache.scope = persistentCache.scope.copy(userId = "user-b")
            }
        }
        transition.start()
        transition.join()

        serviceCallbacks.first().onSuccess(QRemoteConfigList(listOf(oldConfig)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("user-a", "user-b"), requestUsers)
        verify { callback wasNot Called }
        assertTrue(persistentCache.savedConfigs.isEmpty())

        serviceCallbacks.last().onSuccess(QRemoteConfigList(listOf(currentConfig)))

        verify(exactly = 1) { callback.onSuccess(match { it.remoteConfigs == listOf(currentConfig) }) }
        verify(exactly = 0) { callback.onError(any()) }
        assertEquals(listOf(currentConfig), persistentCache.savedConfigs)
        assertEquals(listOf("user-b"), persistentCache.savedScopes.map { it.userId })
    }

    @Test
    fun `attach from a background thread makes the cache immediately stale`() {
        // given - a cached config for a stable user
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)

        // when - an attach lands from a non-main thread
        val backgroundThread = Thread {
            manager.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true))
        }
        backgroundThread.start()
        backgroundThread.join()

        // then - before the posted cleanup drains, the fast path must already
        // reject the pre-attach config (the bump is synchronous on the caller
        // thread for every invalidation seam, not only the public API)
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", callback)
        verify(exactly = 0) { callback.onSuccess(any()) }
    }

    @Test
    fun `rate-limited load delivers the bundled fallback`() {
        // given - a bundled fallback exists and a load is in flight
        userStateProvider.stable = true
        val fallbackConfig = mockk<QRemoteConfig>(relaxed = true)
        every { fallbackConfig.source.contextKey } returns "ctx"
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(fallbackConfig)),
        )
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the request is short-circuited by the local rate limiter
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.ApiRateLimitExceeded))

        // then - the bundled payload is served instead of a hard error; since
        // fallbacks are no longer cached, offline repeat calls hit the limiter
        // instead of the old cached-fallback fast path, and must still get
        // the config
        verify(exactly = 1) { loadCallback.onSuccess(fallbackConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
    }

    @Test
    fun `server timeout and rate limit responses deliver single persistent last known good`() {
        userStateProvider.stable = true
        listOf(408, 429).forEach { statusCode ->
            val contextKey = "ctx_$statusCode"
            val lastKnownGood = remoteConfigFor(contextKey)
            persistentCache.save(lastKnownGood)
            val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
            val serviceCallback = slot<QonversionRemoteConfigCallback>()
            every { mockRemoteConfigService.loadRemoteConfig(contextKey, capture(serviceCallback)) } just runs
            every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
                firstArg<QonversionEmptyCallback?>()?.onComplete()
            }

            manager.loadRemoteConfig(contextKey, callback)
            shadowOf(Looper.getMainLooper()).idle()
            serviceCallback.captured.onError(
                QonversionError(QonversionErrorCode.BackendError, httpCode = statusCode),
            )

            verify(exactly = 1) { callback.onSuccess(lastKnownGood) }
            verify(exactly = 0) { callback.onError(any()) }
        }
    }

    @Test
    fun `server timeout and rate limit responses deliver list persistent last known good`() {
        userStateProvider.stable = true
        listOf(408, 429).forEach { statusCode ->
            val contextKey = "ctx_$statusCode"
            val lastKnownGood = remoteConfigFor(contextKey)
            persistentCache.save(lastKnownGood)
            val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
            val serviceCallback = slot<QonversionRemoteConfigListCallback>()
            every {
                mockRemoteConfigService.loadRemoteConfigs(listOf(contextKey), false, capture(serviceCallback))
            } just runs
            every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
                firstArg<QonversionEmptyCallback?>()?.onComplete()
            }

            manager.loadRemoteConfigList(listOf(contextKey), false, callback)
            shadowOf(Looper.getMainLooper()).idle()
            serviceCallback.captured.onError(
                QonversionError(QonversionErrorCode.BackendError, httpCode = statusCode),
            )

            verify(exactly = 1) {
                callback.onSuccess(match { it.remoteConfigs == listOf(lastKnownGood) })
            }
            verify(exactly = 0) { callback.onError(any()) }
        }
    }

    @Test
    fun `response parsing failure delivers persistent fallback for single and list`() {
        userStateProvider.stable = true
        val singleConfig = remoteConfigFor("single")
        val listConfig = remoteConfigFor("list")
        persistentCache.save(singleConfig)
        persistentCache.save(listConfig)
        val singleCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val listCallback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val singleServiceCallback = slot<QonversionRemoteConfigCallback>()
        val listServiceCallback = slot<QonversionRemoteConfigListCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("single", capture(singleServiceCallback)) } just runs
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("list"), false, capture(listServiceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("single", singleCallback)
        manager.loadRemoteConfigList(listOf("list"), false, listCallback)
        shadowOf(Looper.getMainLooper()).idle()
        val parsingError = QonversionError(QonversionErrorCode.ResponseParsingFailed)
        singleServiceCallback.captured.onError(parsingError)
        listServiceCallback.captured.onError(parsingError)

        verify(exactly = 1) { singleCallback.onSuccess(singleConfig) }
        verify(exactly = 0) { singleCallback.onError(any()) }
        verify(exactly = 1) { listCallback.onSuccess(match { it.remoteConfigs == listOf(listConfig) }) }
        verify(exactly = 0) { listCallback.onError(any()) }
        assertEquals(singleConfig, persistentCache.get("single"))
        assertEquals(listConfig, persistentCache.get("list"))
    }

    @Test
    fun `unknown authentication and client errors never deliver local fallback`() {
        userStateProvider.stable = true
        val nonTransientErrors = listOf(
            QonversionError(QonversionErrorCode.Unknown),
            QonversionError(QonversionErrorCode.Unknown, httpCode = 503),
            QonversionError(QonversionErrorCode.InvalidCredentials),
            QonversionError(QonversionErrorCode.InvalidCredentials, httpCode = 503),
            QonversionError(QonversionErrorCode.BackendError, httpCode = 400),
        )
        nonTransientErrors.forEachIndexed { index, error ->
            val contextKey = "non_transient_$index"
            val stale = remoteConfigFor(contextKey)
            persistentCache.save(stale)
            val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
            val serviceCallback = slot<QonversionRemoteConfigCallback>()
            every { mockRemoteConfigService.loadRemoteConfig(contextKey, capture(serviceCallback)) } just runs
            every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
                firstArg<QonversionEmptyCallback?>()?.onComplete()
            }

            manager.loadRemoteConfig(contextKey, callback)
            shadowOf(Looper.getMainLooper()).idle()
            serviceCallback.captured.onError(error)

            verify(exactly = 1) { callback.onError(error) }
            verify(exactly = 0) { callback.onSuccess(stale) }
        }
    }

    @Test
    fun `invalidation mid-flight does not re-issue a load nobody awaits`() {
        // given - a load with NO waiting callback is in flight
        userStateProvider.stable = true
        val serviceCallbacks = mutableListOf<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallbacks)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", null)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the cache is invalidated mid-flight, then the response lands
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallbacks.first().onSuccess(remoteConfigFor("ctx"))

        // then - no waiter means no retry; the superseded response is simply
        // not cached and the state is left refetchable
        verify(exactly = 1) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }
        val state = loadingStates()["ctx"]
        assertNotNull(state)
        assertEquals(null, state?.loadedConfig)
        assertEquals(false, state?.isInProgress)
    }

    @Test
    fun `attach invalidation prevents an in-flight list load from re-caching stale configs`() {
        // given - a list load is in flight when the attach lands (the map is
        // NOT replaced on attach, so the generation guard is the only barrier)
        userStateProvider.stable = true
        val listServiceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(listServiceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        val listCallback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        manager.loadRemoteConfigList(listOf("ctx"), false, listCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the attach invalidates mid-flight, then the pre-attach list lands
        manager.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val staleConfig = mockk<QRemoteConfig>(relaxed = true)
        every { staleConfig.source.contextKey } returns "ctx"
        listServiceCallback.captured.onSuccess(QRemoteConfigList(listOf(staleConfig)))

        // then - nothing from the stale list is cached (the wrapper would have
        // created a loading state for the key had it cached the config), but
        // the caller still receives the evaluation the load started with —
        // this pins the documented list contract, whose failure mode is a
        // callback that never fires
        assertEquals(false, loadingStates().containsKey("ctx"))
        verify(exactly = 1) { listCallback.onSuccess(any()) }
    }

    @Test
    fun `invalidateRemoteConfigsCache from a background thread defers the map mutation but makes the cache immediately stale`() {
        // given - cached configs for a stable user
        userStateProvider.stable = true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        val otherConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        loadingStates()["other"] = QRemoteConfigManager.LoadingState(loadedConfig = otherConfig)

        // when - invalidated from a non-main thread
        val backgroundThread = Thread { manager.invalidateRemoteConfigsCache() }
        backgroundThread.start()
        backgroundThread.join()

        // then - the map mutation was posted to the main looper, not applied on
        // the background thread (main-thread confinement, SUP3-188 class)...
        assertEquals(cachedConfig, loadingStates()["ctx"]?.loadedConfig)

        // ...but the generation bump is synchronous, so the cached fast path
        // is already stale: a load issued before the posted cleanup drains
        // must not be served the pre-invalidation config
        val callback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", callback)
        verify(exactly = 0) { callback.onSuccess(any()) }

        // and the posted cleanup still lands on the main thread
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(null, loadingStates()["other"]?.loadedConfig)
    }

    @Test
    fun `concurrent invalidateRemoteConfigsCache and loadRemoteConfig do not throw`() {
        // Same SUP3-188 class as the tests above: the public invalidation can be
        // called from any thread while the main thread mutates loadingStates.
        userStateProvider.stable = false
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val iterations = 1_000

        val invalidator = Thread {
            try {
                repeat(iterations) { manager.invalidateRemoteConfigsCache() }
            } catch (t: Throwable) {
                errors.add(t)
            }
        }

        val mainLooper = shadowOf(Looper.getMainLooper())
        invalidator.start()
        try {
            repeat(iterations) { i ->
                manager.loadRemoteConfig("ctx_$i", null)
                mainLooper.idle()
            }
        } catch (t: Throwable) {
            errors.add(t)
        }
        invalidator.join()
        mainLooper.idle()

        assertTrue("Concurrent access threw: $errors", errors.isEmpty())
    }

    @Test
    fun `concurrent invalidation with live loads keeps only current-generation configs cached`() {
        // Unlike the CME stress test above, the user is STABLE here, so every
        // load runs the full generation-capture / cache-write path while a
        // background thread keeps bumping the generation.
        userStateProvider.stable = true
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        val pendingResponses = ArrayDeque<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig(any(), any()) } answers {
            pendingResponses.add(secondArg())
        }
        val config = mockk<QRemoteConfig>(relaxed = true)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val iterations = 300

        val invalidator = Thread {
            try {
                repeat(iterations) { manager.invalidateRemoteConfigsCache() }
            } catch (t: Throwable) {
                errors.add(t)
            }
        }

        val mainLooper = shadowOf(Looper.getMainLooper())
        invalidator.start()
        try {
            repeat(iterations) { i ->
                manager.loadRemoteConfig("ctx_${i % 8}", null)
                mainLooper.idle()
                while (pendingResponses.isNotEmpty()) {
                    pendingResponses.removeFirst().onSuccess(config)
                    mainLooper.idle()
                }
            }
        } catch (t: Throwable) {
            errors.add(t)
        }
        invalidator.join()
        mainLooper.idle()
        while (pendingResponses.isNotEmpty()) {
            pendingResponses.removeFirst().onSuccess(config)
            mainLooper.idle()
        }

        assertTrue("Concurrent access threw: $errors", errors.isEmpty())
        // The stamp-guard invariant: once everything settles, any config still
        // cached must carry the final generation - the write guard must never
        // have let a superseded response in, and every older write must have
        // been swept by a later posted cleanup.
        val finalGeneration = manager
            .getPrivateField<java.util.concurrent.atomic.AtomicInteger>("invalidationGeneration")
            .get()
        loadingStates().values.forEach { state ->
            if (state.loadedConfig != null) {
                assertEquals(finalGeneration, state.generation)
            }
        }
    }

    @Test
    fun `fallback config is delivered without being cached`() {
        // given - a bundled fallback exists and a load is in flight
        userStateProvider.stable = true
        val fallbackConfig = mockk<QRemoteConfig>(relaxed = true)
        every { fallbackConfig.source.contextKey } returns "ctx"
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(fallbackConfig)),
        )
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the network fails in a fallback-eligible way
        serviceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        // then - the fallback is delivered but NOT pinned into the cache, so
        // the next call retries the network instead of serving the fallback
        // until the next invalidation
        verify(exactly = 1) { loadCallback.onSuccess(fallbackConfig) }
        val state = loadingStates()["ctx"]
        assertNotNull(state)
        assertEquals(null, state?.loadedConfig)
        assertEquals(false, state?.isInProgress)
    }

    @Test
    fun `named context never receives the empty-context bundled fallback`() {
        userStateProvider.stable = true
        val emptyContextFallback = remoteConfigFor(null)
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(emptyContextFallback)),
        )
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("missing", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("missing", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()
        val networkError = QonversionError(QonversionErrorCode.NetworkConnectionFailed)
        serviceCallback.captured.onError(networkError)

        verify(exactly = 0) { loadCallback.onSuccess(any()) }
        verify(exactly = 1) { loadCallback.onError(networkError) }
    }

    @Test
    fun `fallback list configs are delivered without being cached`() {
        // given - a bundled fallback exists and a list load is in flight
        userStateProvider.stable = true
        val fallbackConfig = mockk<QRemoteConfig>(relaxed = true)
        every { fallbackConfig.source.contextKey } returns "ctx"
        every { mockFallbacksService.obtainFallbackData() } returns QFallbackObject(
            offerings = null,
            productPermissions = null,
            remoteConfigList = QRemoteConfigList(listOf(fallbackConfig)),
        )
        val listCallback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
        val listServiceCallback = slot<QonversionRemoteConfigListCallback>()
        every {
            mockRemoteConfigService.loadRemoteConfigs(listOf("ctx"), false, capture(listServiceCallback))
        } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfigList(listOf("ctx"), false, listCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the network fails in a fallback-eligible way
        listServiceCallback.captured.onError(QonversionError(QonversionErrorCode.NetworkConnectionFailed))

        // then - the fallback list is delivered but nothing is cached (the
        // wrapper would have created a loading state had it cached anything)
        verify(exactly = 1) { listCallback.onSuccess(match { it.remoteConfigs == listOf(fallbackConfig) }) }
        assertEquals(false, loadingStates().containsKey("ctx"))
    }

    // region v2 identity bridge producer side
    //
    // QonversionInternalRemoteConfigV2WiringTest exercises the CONSUMER side of the bridge by
    // firing it manually, so these tests pin the PRODUCER side: the v1 manager entry points must
    // actually emit the bridge events, or v2 scope switching on identify/logout silently dies
    // while every wiring test stays green.

    @Test
    fun `onUserUpdate fires identityScopeChanged on the v2 bridge exactly once`() {
        var identityScopeChanges = 0
        var targetingInvalidations = 0
        manager.identityBridge.onIdentityScopeChanged = { identityScopeChanges++ }
        manager.identityBridge.onTargetingInvalidated = { targetingInvalidations++ }

        manager.onUserUpdate()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, identityScopeChanges)
        assertEquals(0, targetingInvalidations)
    }

    @Test
    fun `invalidateRemoteConfigsCache fires targetingInvalidated on the v2 bridge exactly once`() {
        var identityScopeChanges = 0
        var targetingInvalidations = 0
        manager.identityBridge.onIdentityScopeChanged = { identityScopeChanges++ }
        manager.identityBridge.onTargetingInvalidated = { targetingInvalidations++ }

        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, targetingInvalidations)
        assertEquals(0, identityScopeChanges)
    }

    @Test
    fun `a throwing v2 bridge observer does not break the v1 identity flow`() {
        manager.identityBridge.onIdentityScopeChanged = { throw RuntimeException("v2 observer boom") }
        manager.identityBridge.onTargetingInvalidated = { throw RuntimeException("v2 observer boom") }
        var identityUpdated = false

        // Neither call may propagate the observer's exception.
        manager.onUserUpdate { identityUpdated = true }
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()

        // The v1 side of onUserUpdate (the identity mutation) still ran to completion.
        assertTrue(identityUpdated)
    }

    // endregion

    private fun listRequests() =
        manager.getPrivateField<List<*>>("listRequests")

    private fun loadingStates() =
        manager.getPrivateField<MutableMap<String?, QRemoteConfigManager.LoadingState>>("loadingStates")

    private fun remoteConfigFor(contextKey: String?): QRemoteConfig {
        val config = mockk<QRemoteConfig>()
        every { config.source.contextKey } returns contextKey
        return config
    }

    private class FakeRemoteConfigCache : RemoteConfigCache {
        private data class PendingMutation(
            val apply: () -> Unit,
            val completion: (Boolean) -> Unit,
        )

        val savedConfigs = mutableListOf<QRemoteConfig>()
        var scope = RemoteConfigCacheScope("project", "Production", "user-a")
        val savedScopes = mutableListOf<RemoteConfigCacheScope>()
        var mutationCount = 0
        var deferDurableMutations = false
        private val pendingMutations = ArrayDeque<PendingMutation>()
        private val scopedConfigs = linkedMapOf<RemoteConfigCacheScope, LinkedHashMap<String?, QRemoteConfig>>()

        override fun currentScope(): RemoteConfigCacheScope = scope

        override fun save(remoteConfig: QRemoteConfig) {
            save(scope, remoteConfig)
        }

        override fun save(scope: RemoteConfigCacheScope, remoteConfig: QRemoteConfig) {
            mutationCount += 1
            savedConfigs += remoteConfig
            savedScopes += scope
            scopedConfigs.getOrPut(scope, ::linkedMapOf)[remoteConfig.source.contextKey] = remoteConfig
        }

        override fun save(
            scope: RemoteConfigCacheScope,
            remoteConfig: QRemoteConfig,
            completion: (Boolean) -> Unit,
        ) = enqueueDurableMutation({ save(scope, remoteConfig) }, completion)

        override fun remove(contextKey: String?) {
            remove(scope, contextKey)
        }

        override fun remove(scope: RemoteConfigCacheScope, contextKey: String?) {
            mutationCount += 1
            scopedConfigs[scope]?.remove(contextKey)
            savedConfigs.removeAll { it.source.contextKey == contextKey }
        }

        override fun remove(
            scope: RemoteConfigCacheScope,
            contextKey: String?,
            completion: (Boolean) -> Unit,
        ) = enqueueDurableMutation({ remove(scope, contextKey) }, completion)

        override fun replaceAll(remoteConfigs: List<QRemoteConfig>) {
            replaceAll(scope, remoteConfigs)
        }

        override fun replaceAll(scope: RemoteConfigCacheScope, remoteConfigs: List<QRemoteConfig>) {
            mutationCount += 1
            scopedConfigs[scope] = linkedMapOf()
            savedConfigs.clear()
            remoteConfigs.forEach { remoteConfig ->
                savedConfigs += remoteConfig
                savedScopes += scope
                scopedConfigs.getValue(scope)[remoteConfig.source.contextKey] = remoteConfig
            }
        }

        override fun replaceAll(
            scope: RemoteConfigCacheScope,
            remoteConfigs: List<QRemoteConfig>,
            completion: (Boolean) -> Unit,
        ) = enqueueDurableMutation({ replaceAll(scope, remoteConfigs) }, completion)

        override fun replaceRequested(
            requestedContextKeys: Set<String?>,
            remoteConfigs: List<QRemoteConfig>,
        ) {
            replaceRequested(scope, requestedContextKeys, remoteConfigs)
        }

        override fun replaceRequested(
            scope: RemoteConfigCacheScope,
            requestedContextKeys: Set<String?>,
            remoteConfigs: List<QRemoteConfig>,
        ) {
            mutationCount += 1
            val scoped = scopedConfigs.getOrPut(scope, ::linkedMapOf)
            requestedContextKeys.forEach { contextKey ->
                scoped.remove(contextKey)
                savedConfigs.removeAll { it.source.contextKey == contextKey }
            }
            remoteConfigs.forEach { remoteConfig ->
                savedConfigs += remoteConfig
                savedScopes += scope
                scoped[remoteConfig.source.contextKey] = remoteConfig
            }
        }

        override fun replaceRequested(
            scope: RemoteConfigCacheScope,
            requestedContextKeys: Set<String?>,
            remoteConfigs: List<QRemoteConfig>,
            completion: (Boolean) -> Unit,
        ) = enqueueDurableMutation(
            { replaceRequested(scope, requestedContextKeys, remoteConfigs) },
            completion,
        )

        fun completeNextDurableMutation(success: Boolean) {
            val mutation = pendingMutations.removeFirst()
            if (success) mutation.apply()
            mutation.completion(success)
        }

        private fun enqueueDurableMutation(apply: () -> Unit, completion: (Boolean) -> Unit) {
            if (deferDurableMutations) {
                pendingMutations.addLast(PendingMutation(apply, completion))
            } else {
                apply()
                completion(true)
            }
        }

        override fun get(contextKey: String?): QRemoteConfig? = get(scope, contextKey)

        override fun get(scope: RemoteConfigCacheScope, contextKey: String?): QRemoteConfig? =
            scopedConfigs[scope]?.get(contextKey)

        override fun getAll(): QRemoteConfigList = getAll(scope)

        override fun getAll(scope: RemoteConfigCacheScope): QRemoteConfigList =
            QRemoteConfigList(scopedConfigs[scope]?.values.orEmpty().toList())
    }

    // Hand-written fake instead of a mockk: isUserStable is read thousands of times inside
    // the concurrent stress loops, and driving a mockk proxy at that volume trips a
    // byte-buddy instrumentation assertion under the CI JDK. A plain object keeps the hot
    // path mock-free.
    private class FakeUserStateProvider : UserStateProvider {
        @Volatile
        var stable: Boolean = false
        override val isUserStable: Boolean get() = stable
    }
}
