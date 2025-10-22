package io.qonversion.nocodes.internal

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.qonversion.nocodes.NoCodesConfig
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.di.DependenciesAssembly
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.screen.service.ScreenService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

private const val PROJECT_KEY = "V4pK6FQo3PiDPj_2vYO1qZpNBbFXNP-a"

private const val VALID_CONTEXT_KEY = "test_context_key"
private const val ID_FOR_SCREEN_BY_CONTEXT_KEY = "KBxnTzQs"
private const val NON_EXISTENT_CONTEXT_KEY = "non_existent_test_context_key"

private const val VALID_SCREEN_ID = "RkgXghGq"
private const val CONTEXT_KEY_FOR_SCREEN_BY_ID = "another_test_context_key"
private const val NON_EXISTENT_SCREEN_ID = "non_existent_screen_id"

@RunWith(AndroidJUnit4::class)
internal class NoCodesOutagerIntegrationTest {

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

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
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

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
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

                assertEquals("Screen ID for first screen should match", VALID_SCREEN_ID, screens[1].id)
                assertEquals("Context key for first screen should match", CONTEXT_KEY_FOR_SCREEN_BY_ID, screens[1].contextKey)

                signal.countDown()
            } catch (e: Exception) {
                fail("Should not fail: ${e.message}")
            }
        }

        signal.await()
    }

    private fun getScreenService(projectKey: String = PROJECT_KEY): ScreenService {
        val config = NoCodesConfig.Builder(
            ApplicationProvider.getApplicationContext(),
            projectKey
        )
            .setProxyURL("<paste outager link here>")
            .setLogLevel(LogLevel.Verbose)
            .build()

        val internalConfig = InternalConfig(config)
        val dependenciesAssembly = DependenciesAssembly.Builder(
            config.application,
            internalConfig
        ).build()

        return dependenciesAssembly.screenService()
    }
}
