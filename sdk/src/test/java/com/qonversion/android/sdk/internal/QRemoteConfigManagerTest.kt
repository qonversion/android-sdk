package com.qonversion.android.sdk.internal

import android.os.Build
import android.os.Looper
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
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

    private lateinit var manager: QRemoteConfigManager

    @Before
    fun setUp() {
        clearAllMocks()

        manager = QRemoteConfigManager(mockRemoteConfigService, mockFallbacksService)
        manager.userStateProvider = userStateProvider
        manager.userPropertiesManager = mockUserPropertiesManager
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

        // then - the cached config is delivered synchronously, without hitting the service
        verify(exactly = 1) { callback.onSuccess(cachedConfig) }
        verify { mockRemoteConfigService wasNot Called }
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

        // then - the cached list is delivered synchronously, without hitting the service
        verify(exactly = 1) { callback.onSuccess(any()) }
        verify { mockRemoteConfigService wasNot Called }
    }

    @Test
    fun `every attach and detach entry point invalidates named-key cached configs`() {
        // given - all four entry points are addressed by entity id only, so the
        // SDK cannot know which context key is served and must drop every cache
        userStateProvider.stable = true
        val entryPoints = listOf<Pair<String, (QRemoteConfigManager) -> Unit>>(
            "attachUserToRemoteConfiguration" to { it.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true)) },
            "detachUserFromRemoteConfiguration" to { it.detachUserFromRemoteConfiguration("config_id", mockk(relaxed = true)) },
            "attachUserToExperiment" to { it.attachUserToExperiment("experiment_id", "group_id", mockk(relaxed = true)) },
            "detachUserFromExperiment" to { it.detachUserFromExperiment("experiment_id", mockk(relaxed = true)) },
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
            // their pending callbacks are preserved (non-destructive invalidation)
            assertEquals("$name must drop the empty-key config", null, loadingStates()[null]?.loadedConfig)
            assertEquals("$name must drop the named-key config", null, loadingStates()["ctx"]?.loadedConfig)
            assertEquals("$name must preserve pending callbacks", 1, loadingStates()["ctx"]?.callbacks?.size)
        }
    }

    @Test
    fun `attach invalidation prevents an in-flight load from re-caching a stale config`() {
        // given - a load is in flight when the attach lands
        userStateProvider.stable = true
        val loadCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("ctx", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }

        manager.loadRemoteConfig("ctx", loadCallback)
        shadowOf(Looper.getMainLooper()).idle()

        // when - the attach invalidates mid-flight, then the pre-attach response lands
        manager.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val staleConfig = mockk<QRemoteConfig>(relaxed = true)
        serviceCallback.captured.onSuccess(staleConfig)

        // then - the stale (pre-attach) evaluation must not be re-cached, but
        // the response is still DELIVERED to the waiting caller and the state
        // is left refetchable (the guard skips only the cache write)
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
        verify(exactly = 1) { loadCallback.onSuccess(staleConfig) }
        assertEquals(false, loadingStates()["ctx"]?.isInProgress)
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

        manager.loadRemoteConfigList(listOf("ctx"), false, mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()

        // when - the attach invalidates mid-flight, then the pre-attach list lands
        manager.attachUserToRemoteConfiguration("config_id", mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        val staleConfig = mockk<QRemoteConfig>(relaxed = true)
        every { staleConfig.source.contextKey } returns "ctx"
        listServiceCallback.captured.onSuccess(QRemoteConfigList(listOf(staleConfig)))

        // then - nothing from the stale list is cached
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
    }

    @Test
    fun `refreshRemoteConfigs drops caches non-destructively and guards in-flight loads`() {
        // given - cached configs with a pending callback, plus a load in flight
        userStateProvider.stable = true
        val pendingCallback = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        loadingStates()[null] = QRemoteConfigManager.LoadingState(loadedConfig = mockk<QRemoteConfig>(relaxed = true))
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(
            loadedConfig = mockk<QRemoteConfig>(relaxed = true),
            callbacks = mutableListOf(pendingCallback),
        )
        val serviceCallback = slot<QonversionRemoteConfigCallback>()
        every { mockRemoteConfigService.loadRemoteConfig("in-flight", capture(serviceCallback)) } just runs
        every { mockUserPropertiesManager.forceSendProperties(any()) } answers {
            firstArg<QonversionEmptyCallback?>()?.onComplete()
        }
        manager.loadRemoteConfig("in-flight", null)
        shadowOf(Looper.getMainLooper()).idle()

        // when
        manager.refreshRemoteConfigs()
        shadowOf(Looper.getMainLooper()).idle()
        serviceCallback.captured.onSuccess(mockk<QRemoteConfig>(relaxed = true))

        // then - every cached config dropped, callbacks preserved, and the
        // pre-refresh in-flight response is not re-cached (generation guard)
        assertEquals(null, loadingStates()[null]?.loadedConfig)
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
        assertEquals(1, loadingStates()["ctx"]?.callbacks?.size)
        assertEquals(null, loadingStates()["in-flight"]?.loadedConfig)
    }

    private fun listRequests() =
        manager.getPrivateField<List<*>>("listRequests")

    private fun loadingStates() =
        manager.getPrivateField<MutableMap<String?, QRemoteConfigManager.LoadingState>>("loadingStates")

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
