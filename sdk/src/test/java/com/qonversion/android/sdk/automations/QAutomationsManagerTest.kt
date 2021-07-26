package com.qonversion.android.sdk.automations

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.qonversion.android.sdk.*
import com.qonversion.android.sdk.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.dto.automations.Screen
import com.qonversion.android.sdk.logger.ConsoleLogger
import com.qonversion.android.sdk.automations.mvp.ScreenActivity
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

    private val fieldIsAppBackground = "isAppBackground"
    private val pushTokenKey = "push_token_key"
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
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = WeakReference(delegate)
            every {
                mockEventMapper.getEventFromRemoteMessage(any())
            } returns null

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse()
            automationsManager.automationsDelegate = null

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage()
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
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse(false)
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage()
            mockActionPointsResponse()
            mockScreensResponse(false)
            automationsManager.automationsDelegate = WeakReference(delegate)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
            val remoteMessage = mockRemoteMessage(false)

            // when
            val result = automationsManager.handlePushIfPossible(remoteMessage)

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
        fun `should set and save token when it is new`() {
            // given
            val newToken = "newToken"
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null
            automationsManager.mockPrivateField(fieldIsAppBackground, false)

            // when
            automationsManager.setPushToken(newToken)

            // then
            verifyOrder {
                mockPrefs.getString(pushTokenKey, "")
                mockRepository.setPushToken(newToken)
                mockEditor.putString(pushTokenKey, newToken)
                mockEditor.apply()
            }
        }

        @Test
        fun `shouldn't set and save token when it is old`() {
            // given
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
        fun `shouldn't set and save token when it is empty`() {
            // given
            val newToken = ""
            every {
                mockPrefs.getString(pushTokenKey, "")
            } returns null
            automationsManager.mockPrivateField(fieldIsAppBackground, false)

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

        every {
            anyConstructed<Intent>().addFlags(any())
        } answers { mockIntent }
    }

    private fun mockLogger() {
        mockkConstructor(ConsoleLogger::class)
        every { anyConstructed<ConsoleLogger>().debug(any()) } just Runs
        every { anyConstructed<ConsoleLogger>().release(any()) } just Runs
    }
}