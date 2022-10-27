package com.qonversion.android.sdk.automations

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.internal.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.internal.dto.automations.Screen
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
import com.qonversion.android.sdk.internal.AppState
import com.qonversion.android.sdk.internal.QonversionRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

class QAutomationsManagerTest {
    private val mockRepository: QonversionRepository = mockk(relaxed = true)
    private val mockActivity: Activity = mockk(relaxed = true)
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val mockEventMapper: AutomationsEventMapper = mockk(relaxed = true)
    private val mockApplication: Application = mockk(relaxed = true)

    private lateinit var mockIntent: Intent
    private lateinit var automationsManager: QAutomationsManager

    private val fieldPendingToken = "pendingToken"
    private val pushTokenKey = "com.qonversion.keys.push_token_key"
    private val pendingPushTokenKey = "com.qonversion.keys.pending_push_token_key"
    private val screenId = "ZNkQaNy6"
    private val html = "<html><body>Screen 2 Content<body></html>"
    private val delegate = object : AutomationsDelegate {
        override fun contextForScreenIntent(): Activity {
            return mockActivity
        }

        override fun automationsDidShowScreen(screenId: String) {}

        override fun automationsDidStartExecuting(actionResult: QActionResult) {}

        override fun automationsDidFailExecuting(actionResult: QActionResult) {}

        override fun automationsDidFinishExecuting(actionResult: QActionResult) {}

        override fun automationsFinished() {}
    }

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockLogger()
        mockIntent()
        mockSharedPreferences()

        automationsManager =
            QAutomationsManager(mockRepository, mockPrefs, mockEventMapper, mockApplication)
    }

    @Nested
    inner class HandlePushIfPossible {
        @Test
        fun `should show screen on Qonversion push when screen and actionPoints requests succeeded`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verifySequence {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityWasStarted(mockActivity)
        }

        @Test
        fun `should show screen on Qonversion push when trigger event is null`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = WeakReference(delegate)
            every {
                mockEventMapper.getEventFromRemoteMessage(any())
            } returns null

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verifySequence {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityWasStarted(mockActivity)
        }

        @Test
        fun `should show screen on Qonversion push when delegate is null`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = null

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verifySequence {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityWasStarted(mockApplication)
        }

        @Test
        fun `shouldn't show screen on Qonversion push when shouldHandleEvent delegate returns false`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = WeakReference(object : AutomationsDelegate {
                override fun contextForScreenIntent(): Activity = mockActivity

                override fun shouldHandleEvent(
                    event: AutomationsEvent,
                    payload: MutableMap<String, String>
                ): Boolean = false
            })

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verify {
                mockRepository wasNot Called
            }
            verifyActivityWasNotStarted()
        }

        @Test
        fun `shouldn't show screen on Qonversion push when actionPoints request failed`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse(false)
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
            }
            verify(exactly = 0) {
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityWasNotStarted()
        }

        @Test
        fun `shouldn't show screen on Qonversion push when screens request failed`() {
            // given
            val remoteMessageData = mockRemoteMessageData()
            mockActionPointsResponse()
            mockScreensResponse(false)
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityWasNotStarted()
        }

        @Test
        fun `shouldn't show screen on non-Qonversion`() {
            // given
            val remoteMessageData = mockRemoteMessageData(false)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessageData)

            // then
            assertThat(result).isFalse()
            verify {
                mockRepository wasNot called
            }
            verifyActivityWasNotStarted()
        }

        private fun mockActionPointsResponse(isResponseSuccess: Boolean = true) {
            if (isResponseSuccess) {
                every {
                    mockRepository.actionPoints(getQueryParams(), captureLambda(), any())
                } answers {
                    lambda<(ActionPointScreen?) -> Unit>().captured.invoke(
                        ActionPointScreen(screenId)
                    )
                }
            } else {
                every {
                    mockRepository.actionPoints(getQueryParams(), any(), captureLambda())
                } answers {
                    lambda<(QonversionError) -> Unit>().captured.invoke(
                        QonversionError(QonversionErrorCode.BackendError, "Failed to load screen")
                    )
                }
            }
        }

        private fun mockScreensResponse(isResponseSuccess: Boolean = true) {
            if (isResponseSuccess) {
                every {
                    mockRepository.screens(screenId, captureLambda(), any())
                } answers {
                    lambda<(Screen) -> Unit>().captured.invoke(
                        Screen(screenId, html, "ru", "#CCEEFF", "string")
                    )
                }
            } else {
                every {
                    mockRepository.screens(screenId, any(), captureLambda())
                } answers {
                    lambda<(QonversionError) -> Unit>().captured.invoke(
                        QonversionError(QonversionErrorCode.BackendError, "Failed to load screen")
                    )
                }
            }
        }

        private fun mockRemoteMessageData(isQonversionMessage: Boolean = true): Map<String, String> {
            val pickScreen = "qonv.pick_screen"

            return mapOf(
                if (isQonversionMessage) pickScreen to "1" else "some.app" to "1"
            )
        }

        private fun verifyActivityWasNotStarted() {
            verify {
                listOf(mockIntent, mockApplication, mockActivity) wasNot called
            }
        }

        private fun verifyActivityWasStarted(context: Context){
            verify(exactly = 1) {
                mockIntent.putExtra(ScreenActivity.INTENT_HTML_PAGE, html)
                mockIntent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                context.startActivity(withArg { mockIntent })
            }
        }

    }

    @Nested
    inner class SetPushToken {
        @Test
        fun `should send new token when app is in foreground`() {
            // given
            val newToken = "newToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null

            mockLooper()
            Qonversion.appState = AppState.Foreground

            // when
            automationsManager.setPushToken(newToken)

            // then
            verifySequence {
                mockPrefs.getString(pushTokenKey, "")
                mockPrefs.edit()
                mockEditor.putString(pendingPushTokenKey, newToken)
                mockEditor.apply()
                mockRepository.setPushToken(newToken)
            }
        }

        @Test
        fun `shouldn't send new token when app is in background`() {
            // given
            val newToken = "newToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null

            mockLooper()
            Qonversion.appState = AppState.Background

            // when
            automationsManager.setPushToken(newToken)

            // then
            val pendingToken = automationsManager.getPrivateField<String?>(fieldPendingToken)
            assertThat(pendingToken).isEqualTo(newToken)

            verify(exactly = 1) {
                mockPrefs.getString(pushTokenKey, "")
                mockEditor.putString(pendingPushTokenKey, newToken)
                mockEditor.apply()
            }
            verify(exactly = 0) {
                mockRepository.setPushToken(newToken)
            }
        }

        @Test
        fun `shouldn't send an old token`() {
            val oldToken = "oldToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns oldToken

            // when
            automationsManager.setPushToken(oldToken)

            // then
            verify(exactly = 1) {
                mockPrefs.getString(pushTokenKey, "")
            }
            verify {
                listOf(
                    mockEditor,
                    mockRepository
                ) wasNot Called
            }
        }


        @Test
        fun `shouldn't send token when it is empty`() {
            // given
            val newToken = ""
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null

            mockLooper()
            Qonversion.appState = AppState.Foreground

            // when
            automationsManager.setPushToken(newToken)

            // then
            verify(exactly = 1) {
                mockPrefs.getString(pushTokenKey, "")
            }
            verify {
                listOf(
                    mockEditor,
                    mockRepository
                ) wasNot Called
            }
        }
    }

    @Nested
    inner class OnAppForeground {
        @Test
        fun `should send new pending token after app switched to foreground`() {
            // given
            val newToken = "newToken"
            automationsManager.mockPrivateField(fieldPendingToken, newToken)

            // when
            automationsManager.onAppForeground()

            // then
            val pendingToken = automationsManager.getPrivateField<String?>(fieldPendingToken)
            assertThat(pendingToken).isNull()

            verifyOrder {
                mockRepository.setPushToken(newToken)
            }
        }

        @Test
        fun `should not send null pending token after app switched to foreground`() {
            // given
            val nullToken = null
            automationsManager.mockPrivateField(fieldPendingToken, nullToken)

            // when
            automationsManager.onAppForeground()

            // then
            val pendingToken = automationsManager.getPrivateField<String?>(fieldPendingToken)
            assertThat(pendingToken).isNull()

            verify {
                listOf(mockRepository, mockEditor) wasNot called
            }
        }
    }

    private fun getQueryParams(): Map<String, String> {
        val queryParamTypeKey = "type"
        val queryParamActiveKey = "active"
        val queryParamTypeValue = "screen_view"
        val queryParamActiveValue = 1

        val queryParams = HashMap<String, String>()
        return queryParams.apply {
            put(queryParamTypeKey, queryParamTypeValue)
            put(
                queryParamActiveKey,
                queryParamActiveValue.toString()
            )
        }
    }

    private fun mockSharedPreferences() {
        every {
            mockEditor.putString(pushTokenKey, any())
        } returns mockEditor

        every {
            mockEditor.putString(pendingPushTokenKey, any())
        } returns mockEditor

        every {
            mockPrefs.edit()
        } returns mockEditor

        every {
            mockEditor.apply()
        } just runs
    }

    private fun mockIntent() {
        mockkConstructor(Intent::class)
        mockIntent = Intent(mockActivity, ScreenActivity::class.java)

        every {
            anyConstructed<Intent>().putExtra(
                ScreenActivity.INTENT_HTML_PAGE,
                html
            )
        } answers { mockIntent }
        every {
            anyConstructed<Intent>().putExtra(
                ScreenActivity.INTENT_SCREEN_ID,
                screenId
            )
        } answers { mockIntent }

        every {
            anyConstructed<Intent>().addFlags(any())
        } answers { mockIntent }
    }

    private fun mockLogger() {
        mockkConstructor(ConsoleLogger::class)
        every { anyConstructed<ConsoleLogger>().debug(any()) } just Runs
        every { anyConstructed<ConsoleLogger>().release(any()) } just Runs
    }

    private fun mockLooper() {
        val mockLooper = mockk<Looper>()
        mockkStatic(Looper::class)
        every {
            Looper.getMainLooper()
        } returns mockLooper
        every {
            Looper.myLooper()
        } returns mockLooper
    }
}