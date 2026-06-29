package com.qonversion.android.sdk.internal

import android.os.Build
import android.os.Looper
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.internal.provider.UserStateProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.services.QRemoteConfigService
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
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
    private val mockUserStateProvider = mockk<UserStateProvider>(relaxed = true)
    private val mockUserPropertiesManager = mockk<QUserPropertiesManager>(relaxed = true)

    private lateinit var manager: QRemoteConfigManager

    @Before
    fun setUp() {
        clearAllMocks()

        manager = QRemoteConfigManager(mockRemoteConfigService, mockFallbacksService)
        manager.userStateProvider = mockUserStateProvider
        manager.userPropertiesManager = mockUserPropertiesManager
    }

    @Test
    fun `loadRemoteConfigList from a background thread defers the listRequests mutation to the main thread`() {
        // given - the user is not stable, so loadRemoteConfigList enqueues the request
        every { mockUserStateProvider.isUserStable } returns false
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
    fun `handlePendingRequests does not throw when a handled request re-enqueues itself`() {
        // given - three pending list requests queued while the user was not stable
        every { mockUserStateProvider.isUserStable } returns false
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
        every { mockUserStateProvider.isUserStable } returns false
        manager.loadRemoteConfigList(mockk(relaxed = true))
        manager.loadRemoteConfigList(listOf("ctx"), false, mockk(relaxed = true))
        assertEquals(2, listRequests().size)

        // when - the user becomes stable and the pending requests are handled
        every { mockUserStateProvider.isUserStable } returns true
        manager.handlePendingRequests()

        // then - nothing stale lingers to be re-issued on the next launch
        assertEquals(0, listRequests().size)
    }

    @Test
    fun `concurrent loadRemoteConfigList and handlePendingRequests do not throw`() {
        // Reproduces SUP3-188: a caller thread adds to listRequests while the main thread
        // iterates it in handlePendingRequests. Main-thread confinement serialises both
        // onto the main thread, so the ConcurrentModificationException can no longer occur.
        every { mockUserStateProvider.isUserStable } returns false
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)
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
    fun `loadRemoteConfig returns a cached config synchronously on the main thread`() {
        // given - a previously loaded config for the empty context key, user is stable
        every { mockUserStateProvider.isUserStable } returns true
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
        every { mockUserStateProvider.isUserStable } returns true
        val cachedConfig = mockk<QRemoteConfig>(relaxed = true)
        loadingStates()["ctx"] = QRemoteConfigManager.LoadingState(loadedConfig = cachedConfig)
        val callback = mockk<QonversionRemoteConfigListCallback>(relaxed = true)

        // when - called on the test (main) thread, with no looper draining in between
        manager.loadRemoteConfigList(listOf("ctx"), false, callback)

        // then - the cached list is delivered synchronously, without hitting the service
        verify(exactly = 1) { callback.onSuccess(any()) }
        verify { mockRemoteConfigService wasNot Called }
    }

    private fun listRequests() =
        manager.getPrivateField<List<*>>("listRequests")

    private fun loadingStates() =
        manager.getPrivateField<MutableMap<String?, QRemoteConfigManager.LoadingState>>("loadingStates")
}
