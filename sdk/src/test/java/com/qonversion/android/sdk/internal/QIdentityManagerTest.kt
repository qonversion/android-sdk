package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.services.QUserInfoService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

class QIdentityManagerTest {
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)

    private val currentUserID = "currentUserID"
    private val newUserID = "newUserID"

    private lateinit var identityManager: QIdentityManager

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        identityManager = QIdentityManager(mockRepository, mockUserInfoService)
    }

    @Nested
    inner class Identify {
        @Test
        fun `should return non-empty identityID from onSuccess callback`() {
            // given
            val identityID = "identityID"
            var resultUserID: String? = null
            mockIdentifyResponse(identityID)

            every {
                mockUserInfoService.obtainUserID()
            } returns currentUserID

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {
                    resultUserID = identityID
                }
                override fun onError(error: QonversionError) {}
            })

            // then
            assertThat(resultUserID).isEqualTo(identityID)
        }

        @Test
        fun `should return empty identityID from onSuccess callback`() {
            // given
            val identityID = ""
            var resultUserID: String? = null
            mockIdentifyResponse(identityID)

            every {
                mockUserInfoService.obtainUserID()
            } returns currentUserID

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {
                    resultUserID = identityID
                }
                override fun onError(error: QonversionError) {}
            })

            // then
            assertThat(resultUserID).isEqualTo(identityID)
        }

        @Test
        fun `should store non-empty identityID`() {
            // given
            val identityID = "identityID"
            mockIdentifyResponse(identityID)

            every {
                mockUserInfoService.obtainUserID()
            } returns currentUserID

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {}
                override fun onError(error: QonversionError) {}
            })

            // then
            verifySequence {
                mockUserInfoService.obtainUserID()
                mockRepository.identify(newUserID, currentUserID, any(), any())
                mockUserInfoService.storePartnersIdentityId(newUserID)
                mockUserInfoService.storeQonversionUserId(identityID)
            }
        }

        @Test
        fun `should not store empty identityID`() {
            // given
            val identityID = ""
            mockIdentifyResponse(identityID)

            every {
                mockUserInfoService.obtainUserID()
            } returns currentUserID

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {}
                override fun onError(error: QonversionError) {}
            })

            // then
            verifySequence {
                mockUserInfoService.obtainUserID()
                mockRepository.identify(newUserID, currentUserID, any(), any())
                mockUserInfoService.storePartnersIdentityId(newUserID)
            }
        }

        @Test
        fun `should return error from onError callback`() {
            // given
            var qError: QonversionError? = null

            every {
                mockRepository.identify(newUserID, currentUserID, any(), captureLambda())
            } answers {
                lambda<(QonversionError) -> Unit>().captured.invoke(
                    QonversionError(QonversionErrorCode.BackendError)
                )
            }

            every {
                mockUserInfoService.obtainUserID()
            } returns currentUserID

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(identityID: String) {}
                override fun onError(error: QonversionError) {
                    qError = error
                }
            })

            // then
            assertThat(qError).isNotNull
            assertThat(qError!!.code)
                .isEqualTo(QonversionErrorCode.BackendError)
        }

        private fun mockIdentifyResponse(identityID: String) {
            every {
                mockRepository.identify(newUserID, currentUserID, captureLambda(), any())
            } answers {
                lambda<(String) -> Unit>().captured.invoke(
                    identityID
                )
            }
        }
    }

    @Test
    fun logout() {
        // when
        identityManager.logoutIfNeeded()

        //then
        verify(exactly = 1) {
            mockUserInfoService.logoutIfNeeded()
        }
    }
}
