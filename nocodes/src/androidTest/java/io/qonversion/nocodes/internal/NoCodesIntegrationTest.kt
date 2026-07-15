package io.qonversion.nocodes.internal

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.NoCodesConfig
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.dto.QNoCodeScreen
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.interfaces.NoCodesScreenLoadCallback
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.networkLayer.RetryPolicy
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import io.qonversion.nocodes.internal.screen.service.ScreenService
import io.qonversion.nocodes.internal.screen.service.ScreenServiceImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val PROJECT_KEY = "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-a"
private const val INCORRECT_PROJECT_KEY = "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-aaaaa"

private const val VALID_CONTEXT_KEY = "test_context_key"
private const val ID_FOR_SCREEN_BY_CONTEXT_KEY = "KBxnTzQs"
private const val NON_EXISTENT_CONTEXT_KEY = "non_existent_test_context_key"

private const val VALID_SCREEN_ID = "RkgXghGq"
private const val CONTEXT_KEY_FOR_SCREEN_BY_ID = "another_test_context_key"
private const val NON_EXISTENT_SCREEN_ID = "non_existent_screen_id"

private const val CALLBACK_TIMEOUT_SEC = 30L

@RunWith(AndroidJUnit4::class)
internal class NoCodesIntegrationTest {

    @Test
    fun getScreenByContextKey() {
        // given
        val signal = CountDownLatch(1)
        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                val screen = screenService.getScreen(VALID_CONTEXT_KEY)

                // then
                assertNotNull("Screen should not be null", screen)
                assertEquals("Context key should match", VALID_CONTEXT_KEY, screen.contextKey)
                assertEquals("Screen ID should match", ID_FOR_SCREEN_BY_CONTEXT_KEY, screen.id)
                assertNotNull("Screen content should exist", screen.body)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenWithIncorrectProjectKey() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService(INCORRECT_PROJECT_KEY)

        // when
        runBlocking {
            try {
                screenService.getScreen(VALID_CONTEXT_KEY)
                fail("Should fail with incorrect project key")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be BackendError", ErrorCode.BackendError, e.code)
                assertTrue("Error message should contain authentication info",
                    e.message?.contains("401") == true || e.message?.contains("403") == true)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenWithNonExistentContextKey() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                screenService.getScreen(NON_EXISTENT_CONTEXT_KEY)
                fail("Should fail with non-existent context key")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, e.code)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenWithEmptyContextKey() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                screenService.getScreen("")
                fail("Should fail with empty context key")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, e.code)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenById() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                val screen = screenService.getScreenById(VALID_SCREEN_ID)

                // then
                assertNotNull("Screen should not be null", screen)
                assertEquals("Screen ID should match", VALID_SCREEN_ID, screen.id)
                assertEquals("Context key should march", CONTEXT_KEY_FOR_SCREEN_BY_ID, screen.contextKey)
                assertNotNull("Screen content should exist", screen.body)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenByIdWithIncorrectProjectKey() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService(INCORRECT_PROJECT_KEY)

        // when
        runBlocking {
            try {
                screenService.getScreenById(VALID_SCREEN_ID)
                fail("Should fail with incorrect project key")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be BackendError", ErrorCode.BackendError, e.code)
                assertTrue("Error message should contain authentication info",
                    e.message?.contains("401") == true || e.message?.contains("403") == true)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenWithNonExistentId() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                screenService.getScreenById(NON_EXISTENT_SCREEN_ID)
                fail("Should fail with non-existent screen ID")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, e.code)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun getScreenWithEmptyId() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                screenService.getScreenById("")
                fail("Should fail with empty screen ID")
            } catch (e: NoCodesException) {
                // then
                assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, e.code)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should throw NoCodesException: ${e.message}")
            }
        }

        signal.await()
    }

    // loadScreen — load-before-present

    @Test
    fun loadScreenByContextKeyReturnsScreenWithPublicIdentifiers() {
        // given
        val noCodes = getNoCodes()

        // when
        runBlocking {
            val screen = noCodes.loadScreen(VALID_CONTEXT_KEY)

            // then
            // The public identifiers must be readable by consumers outside the module.
            assertEquals("Public context key should match", VALID_CONTEXT_KEY, screen.contextKey)
            assertEquals("Public screen ID should match", ID_FOR_SCREEN_BY_CONTEXT_KEY, screen.id)
        }
    }

    @Test
    fun loadScreenWithNonExistentContextKeyThrowsScreenNotFound() {
        // given
        val noCodes = getNoCodes()

        // when
        runBlocking {
            try {
                noCodes.loadScreen(NON_EXISTENT_CONTEXT_KEY)
                fail("Should fail with non-existent context key")
            } catch (e: NoCodesException) {
                // then
                // A genuinely absent screen is surfaced with a distinct code, so callers can
                // branch "show fallback" vs "transient failure, maybe retry".
                assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, e.code)
            }
        }
    }

    @Test
    fun loadScreenWithCallbackDeliversScreenOnMainThread() {
        // given
        val signal = CountDownLatch(1)
        val noCodes = getNoCodes()
        var loadedScreen: QNoCodeScreen? = null
        var loadError: NoCodesError? = null
        var wasMainThread = false

        // when
        noCodes.loadScreen(VALID_CONTEXT_KEY, object : NoCodesScreenLoadCallback {
            override fun onSuccess(screen: QNoCodeScreen) {
                loadedScreen = screen
                wasMainThread = Looper.myLooper() == Looper.getMainLooper()
                signal.countDown()
            }

            override fun onError(error: NoCodesError) {
                loadError = error
                signal.countDown()
            }
        })

        // then
        assertTrue("Callback should be invoked", signal.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertNull("Should not fail: $loadError", loadError)
        assertEquals("Public context key should match", VALID_CONTEXT_KEY, loadedScreen?.contextKey)
        assertEquals("Public screen ID should match", ID_FOR_SCREEN_BY_CONTEXT_KEY, loadedScreen?.id)
        assertTrue("Callback should be delivered on the main thread", wasMainThread)
    }

    @Test
    fun loadScreenWithCallbackDeliversScreenNotFoundError() {
        // given
        val signal = CountDownLatch(1)
        val noCodes = getNoCodes()
        var loadedScreen: QNoCodeScreen? = null
        var loadError: NoCodesError? = null
        var wasMainThread = false

        // when
        noCodes.loadScreen(NON_EXISTENT_CONTEXT_KEY, object : NoCodesScreenLoadCallback {
            override fun onSuccess(screen: QNoCodeScreen) {
                loadedScreen = screen
                signal.countDown()
            }

            override fun onError(error: NoCodesError) {
                loadError = error
                wasMainThread = Looper.myLooper() == Looper.getMainLooper()
                signal.countDown()
            }
        })

        // then
        assertTrue("Callback should be invoked", signal.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS))
        assertNull("Should not succeed for non-existent context key", loadedScreen)
        assertEquals("Error code should be ScreenNotFound", ErrorCode.ScreenNotFound, loadError?.code)
        assertTrue("Callback should be delivered on the main thread", wasMainThread)
    }

    @Test
    fun loadScreenWarmsCacheForSubsequentLoad() {
        // given
        // Count network executions to prove the second load is served from cache rather than
        // the network — a live-backend-only variant would pass even with no caching at all.
        val dependenciesAssembly = getDependenciesAssembly()
        val countingApiInteractor = CountingApiInteractor(dependenciesAssembly.exponentialApiInteractor())
        val screenService = ScreenServiceImpl(
            dependenciesAssembly.requestConfigurator(),
            countingApiInteractor,
            dependenciesAssembly.screenMapper(),
            null,
            null,
            dependenciesAssembly.logger()
        )

        // when
        runBlocking {
            val firstLoad = screenService.getScreen(VALID_CONTEXT_KEY)
            val secondLoad = screenService.getScreen(VALID_CONTEXT_KEY)

            // then
            assertEquals("Cached load should return the same screen ID", firstLoad.id, secondLoad.id)
            assertEquals(
                "Cached load should return the same context key",
                firstLoad.contextKey,
                secondLoad.contextKey
            )
            assertEquals(
                "Second load should be served from cache without a second network request",
                1,
                countingApiInteractor.executeCallCount
            )
        }
    }

    @Test
    fun preloadScreens() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService()

        // when
        runBlocking {
            try {
                val screens = screenService.preloadScreens()

                // then
                assertNotNull("Screens list should not be null", screens)
                assertEquals("Should preload two screens", screens.size, 2)

                assertEquals("Screen ID for second screen should match", ID_FOR_SCREEN_BY_CONTEXT_KEY, screens[0].id)
                assertEquals("Context key for second screen should match", VALID_CONTEXT_KEY, screens[0].contextKey)
                assertNotNull("First screen content should exist", screens[0].body)

                assertEquals("Screen ID for first screen should match", VALID_SCREEN_ID, screens[1].id)
                assertEquals("Context key for first screen should match", CONTEXT_KEY_FOR_SCREEN_BY_ID, screens[1].contextKey)
                assertNotNull("Second screen content should exist", screens[1].body)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
            }
        }

        signal.await()
    }

    @Test
    fun preloadScreensWithIncorrectProjectKey() {
        // given
        val signal = CountDownLatch(1)

        val screenService = getScreenService(INCORRECT_PROJECT_KEY)

        // when
        runBlocking {
            try {
                val screens = screenService.preloadScreens()

                // then - preload should return empty list on error, not throw exception
                assertNotNull("Screens list should not be null", screens)
                assertTrue("Should return empty list on error", screens.isEmpty())

                signal.countDown()
            } catch (e: Exception) {
                fail("Preload should not throw exception: ${e.message}")
            }
        }

        signal.await()
    }

    private fun getScreenService(projectKey: String = PROJECT_KEY): ScreenService {
        return getDependenciesAssembly(projectKey).screenService()
    }

    private fun getNoCodes(projectKey: String = PROJECT_KEY): NoCodes {
        val config = buildConfig(projectKey)
        val internalConfig = InternalConfig(config)
        val dependenciesAssembly = DependenciesAssembly.Builder(
            config.application,
            internalConfig
        ).build()

        return NoCodesInternal(internalConfig, dependenciesAssembly)
    }

    private fun getDependenciesAssembly(projectKey: String = PROJECT_KEY): DependenciesAssembly {
        val config = buildConfig(projectKey)

        return DependenciesAssembly.Builder(
            config.application,
            InternalConfig(config)
        ).build()
    }

    private fun buildConfig(projectKey: String): NoCodesConfig {
        return NoCodesConfig.Builder(
            ApplicationProvider.getApplicationContext(),
            projectKey
        )
            .setLogLevel(LogLevel.Verbose)
            .build()
    }
}

private class CountingApiInteractor(private val wrapped: ApiInteractor) : ApiInteractor {

    var executeCallCount = 0
        private set

    override suspend fun execute(request: Request): Response {
        executeCallCount++
        return wrapped.execute(request)
    }

    override suspend fun execute(request: Request, retryPolicy: RetryPolicy): Response {
        executeCallCount++
        return wrapped.execute(request, retryPolicy)
    }
}
