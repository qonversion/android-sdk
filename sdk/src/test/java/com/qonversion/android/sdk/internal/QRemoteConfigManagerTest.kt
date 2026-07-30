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
    }

    @Test
    fun `loadRemoteConfigList cache hit does not flush properties while the user is unstable`() {
        // given - cached configs, but the user is mid-identify. The stability
        // gate exists so the flush cannot POST to a switching uid.
        userStateProvider.stable = false
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)

        // when
        manager.loadRemoteConfigList(listOf("ctx"), false, callback)

        // then - the cached list is still served, but nothing is flushed
        verify(exactly = 1) { callback.onSuccess(any()) }
        verify(exactly = 0) { mockUserPropertiesManager.forceSendProperties(any()) }
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
        val staleConfig = mockk<QRemoteConfig>(relaxed = true)
        serviceCallbacks.first().onSuccess(staleConfig)

        // then - the stale evaluation is neither cached nor delivered; the
        // load is re-issued exactly once for the waiting callback
        verify(exactly = 0) { loadCallback.onSuccess(any()) }
        verify(exactly = 2) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }

        // and the fresh response is delivered, cached, and the state settled
        val freshConfig = mockk<QRemoteConfig>(relaxed = true)
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
        val staleConfig = mockk<QRemoteConfig>(relaxed = true)
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
        serviceCallbacks.first().onSuccess(mockk<QRemoteConfig>(relaxed = true))

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
        // triggers a re-issue, and the retry fails without a fallback
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = mockk<QRemoteConfig>(relaxed = true)
        serviceCallbacks.first().onSuccess(supersededConfig)
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.BackendError))

        // then - never worse than before: the superseded evaluation is
        // delivered as a success instead of surfacing the retry error, and
        // nothing is cached
        verify(exactly = 1) { loadCallback.onSuccess(supersededConfig) }
        verify(exactly = 0) { loadCallback.onError(any()) }
        assertEquals(null, loadingStates()["ctx"]?.loadedConfig)
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
        val supersededConfig = mockk<QRemoteConfig>(relaxed = true)
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
        // retry fails without a fallback
        manager.invalidateRemoteConfigsCache()
        shadowOf(Looper.getMainLooper()).idle()
        val supersededConfig = mockk<QRemoteConfig>(relaxed = true)
        serviceCallbacks.first().onSuccess(supersededConfig)
        val callbackB = mockk<QonversionRemoteConfigCallback>(relaxed = true)
        manager.loadRemoteConfig("ctx", callbackB)
        serviceCallbacks.last().onError(QonversionError(QonversionErrorCode.BackendError))

        // then - the never-worse guarantee is uniform: the late joiner gets
        // the baseline too, not the retry error
        verify(exactly = 1) { callbackA.onSuccess(supersededConfig) }
        verify(exactly = 1) { callbackB.onSuccess(supersededConfig) }
        verify(exactly = 0) { callbackA.onError(any()) }
        verify(exactly = 0) { callbackB.onError(any()) }
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
    fun `a user switch mid-flight does not trigger an unrequested re-issue`() {
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
        serviceCallbacks.first().onSuccess(mockk<QRemoteConfig>(relaxed = true))

        // then - the orphaned state must not fire a request nobody awaits
        verify(exactly = 1) { mockRemoteConfigService.loadRemoteConfig("ctx", any()) }
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
        serviceCallbacks.first().onSuccess(mockk<QRemoteConfig>(relaxed = true))

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
