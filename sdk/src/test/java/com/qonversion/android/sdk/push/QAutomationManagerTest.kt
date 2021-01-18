package com.qonversion.android.sdk.push

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.QonversionError
import com.qonversion.android.sdk.QonversionErrorCode
import com.qonversion.android.sdk.QonversionRepository
import com.qonversion.android.sdk.dto.automation.ActionPointScreen
import com.qonversion.android.sdk.dto.automation.Screen
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.push.mvp.ScreenActivity
import com.qonversion.android.sdk.toInt
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QAutomationManagerTest {
    private val mockRepository: QonversionRepository = mockk(relaxed = true)
    private val mockActivity: Activity = mockk(relaxed = true)
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var mockIntent: Intent
    private lateinit var automationManager: QAutomationManager

    private val pushTokenKey = "push_token_key"
    private val screenId = "ZNkQaNy6"
    private val html = "<html><body>Screen 2 Content<body></html>"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockLogger()
        mockIntent()
        mockSharedPreferences()

        automationManager = QAutomationManager(mockRepository, mockPrefs)
        automationManager.automationDelegate = object : QAutomationDelegate {
            override fun provideActivityForScreen(): Activity {
                return mockActivity
            }

            override fun automationFlowFinishedWithAction(action: QAction) {
            }
        }
    }

    @Nested
    inner class HandlePushIfPossible {
        @Test
        fun `returns true when push is qonversion's and screen show completed`() {
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse()

            val result = automationManager.handlePushIfPossible(remoteMessage)
            assertThat(result).isTrue()
            verify(exactly = 1) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityStart(true)
        }

        @Test
        fun `returns true when push is qonversion's and actionPoints request failed`() {
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse(false)

            val result = automationManager.handlePushIfPossible(remoteMessage)
            assertThat(result).isTrue()
            verify(exactly = 1) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
            }
            verify(exactly = 0) {
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityStart(false)
        }

        @Test
        fun `returns true when push is qonversion's and screen request failed`() {
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse(false)

            val result = automationManager.handlePushIfPossible(remoteMessage)
            assertThat(result).isTrue()
            verify(exactly = 1) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityStart(false)
        }

        @Test
        fun `returns false when push is not Qonversion`() {
            val remoteMessage = mockRemoteMessage(false)

            val result = automationManager.handlePushIfPossible(remoteMessage)
            assertThat(result).isFalse()
            verify(exactly = 0) {
                mockRepository.actionPoints(getQueryParams(), any(), any())
                mockRepository.screens(screenId, any(), any())
            }
            verifyActivityStart(false)
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

        private fun mockRemoteMessage(isQonversionMessage: Boolean = true): RemoteMessage {
            val pickScreen = "qonv.pick_screen"

            val remoteMessage = mockk<RemoteMessage>()
            every {
                remoteMessage.data
            } returns mapOf(
                if (isQonversionMessage) pickScreen to "1" else "some.app" to "1"
            )
            return remoteMessage
        }

        private fun verifyActivityStart(isActivityWasStarted: Boolean){
            verify(exactly = isActivityWasStarted.toInt()) {
                mockIntent.putExtra(ScreenActivity.INTENT_HTML_PAGE, html)
                mockIntent.putExtra(ScreenActivity.INTENT_SCREEN_ID, screenId)
                mockActivity.startActivity(withArg { mockIntent })
            }
        }
    }

    @Nested
    inner class SetPushToken {
        @Test
        fun `set token when it is new`() {
            val newToken = "newToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null

            automationManager.setPushToken(newToken)
            verify(exactly = 1) {
                mockRepository.setPushToken(newToken)
                mockEditor.putString(pushTokenKey, newToken)
            }
        }

        @Test
        fun `doesn't set token when it is old`() {
            val oldToken = "oldToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns oldToken

            automationManager.setPushToken(oldToken)
            verify(exactly = 0) {
                mockRepository.setPushToken(any())
                mockEditor.putString(pushTokenKey, any())
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
    }

    private fun mockLogger() {
        mockkConstructor(ConsoleLogger::class)
        every { anyConstructed<ConsoleLogger>().debug(any()) } just Runs
        every { anyConstructed<ConsoleLogger>().release(any()) } just Runs
    }
}