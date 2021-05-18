package com.qonversion.android.sdk

import com.qonversion.android.sdk.services.QUserInfoService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class QIdentityManagerTest {
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)

    private val currentUserID = "currentUserID"
    private val newUserID = "newUserID"
    private lateinit var identityManager: QIdentityManager

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every {
            mockUserInfoService.obtainUserID()
        } returns currentUserID

        identityManager = QIdentityManager(mockRepository, mockUserInfoService)
    }

    @Nested
    inner class Identify {
        @Test
        fun `should call repository identify method with currentUserID only once`() {
            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {}
                override fun onError(error: QonversionError) {}
            })

            // then
            verify(exactly = 1) {
                mockRepository.identify(newUserID, currentUserID, any(), any())
            }
        }

        @Test
        fun `should return non-empty identityID from onSuccess callback`() {
            // given
            val identityID = "identityID"
            var resultUserID: String? = null

            every {
                mockRepository.identify(newUserID, currentUserID, captureLambda(), any())
            } answers {
                lambda<(String) -> Unit>().captured.invoke(
                    identityID
                )
            }

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {
                    resultUserID = identityID
                }

                override fun onError(error: QonversionError) {
                    fail("Shouldn't go into onError callback")
                }
            })

            // then
            assertThat(resultUserID).isEqualTo(identityID)
            verify(exactly = 1) {
                mockUserInfoService.storeIdentity(resultUserID!!)
            }
        }

        @Test
        fun `should return empty identityID from onSuccess callback`() {
            // given
            val identityID = ""
            var resultUserID: String? = null

            every {
                mockRepository.identify(newUserID, currentUserID, captureLambda(), any())
            } answers {
                lambda<(String) -> Unit>().captured.invoke(
                    identityID
                )
            }

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {
                    resultUserID = identityID
                }

                override fun onError(error: QonversionError) {
                    fail("Shouldn't go into onError callback")
                }
            })

            // then
            assertThat(resultUserID).isEqualTo(identityID)
            verify(exactly = 0) {
                mockUserInfoService.storeIdentity(any())
            }
        }

        @Test
        fun `should return qonversion error from onError callback`() {
            // given
            var qError: QonversionError? = null

            every {
                mockRepository.identify(newUserID, currentUserID, any(), captureLambda())
            } answers {
                lambda<(QonversionError) -> Unit>().captured.invoke(
                    QonversionError(QonversionErrorCode.BackendError)
                )
            }

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {
                    fail("Shouldn't go into onSuccess callback")
                }

                override fun onError(error: QonversionError) {
                    qError = error
                }
            })

            // then
            assertThat(qError).isNotNull
            assertThat(qError!!.code)
                .isEqualTo(QonversionErrorCode.BackendError)
        }
    }

    @Test
    fun logout() {
        // when
        identityManager.logout()

        //then
        verify(exactly = 1) {
            mockUserInfoService.logout()
        }
    }
}
