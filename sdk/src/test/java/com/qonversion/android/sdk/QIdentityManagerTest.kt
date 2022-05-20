package com.qonversion.android.sdk

import com.qonversion.android.sdk.services.QUserInfoService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

class QIdentityManagerTest {
    private val mockUserInfoService = mockk<QUserInfoService>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)

    private lateinit var identityManager: QIdentityManager

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        identityManager = QIdentityManager(mockRepository, mockUserInfoService)
    }

    @Nested
    inner class Identify {
        private val currentUserID = "currentUserID"
        private val newUserID = "newUserID"
        private val slotIdentityCallback = slot<IdentityManagerCallback>()
        private val mockError = mockk<QonversionError>()

        @BeforeEach
        fun setUp() {
            identityManager = spyk(identityManager)
            every { mockUserInfoService.obtainUserID() } returns currentUserID
        }

        @Test
        fun `identify fails with unknown error`() {
            // given
            val expectedCode = 400
            var actualError: QonversionError? = null
            var actualCode: Int? = null
            every {
                identityManager.obtainIdentity(newUserID, capture(slotIdentityCallback))
            } answers {
                slotIdentityCallback.captured.onError(mockError, expectedCode)
            }

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(qonversionUserId: String) {
                    fail("Should not be reached")
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    actualError = error
                    actualCode = responseCode
                }
            })

            // then
            verify { identityManager.obtainIdentity(newUserID, any()) }
            verify(exactly = 0) { identityManager.createIdentity(any(), any()) }
            assertThat(actualError).isEqualTo(mockError)
            assertThat(actualCode).isEqualTo(expectedCode)
        }

        @Test
        fun `identify fails with not found error`() {
            // given
            val expectedCode = 404
            every {
                identityManager.obtainIdentity(newUserID, capture(slotIdentityCallback))
            } answers {
                slotIdentityCallback.captured.onError(mockError, expectedCode)
            }
            val callback =  object : IdentityManagerCallback {
                override fun onSuccess(qonversionUserId: String) {
                    fail("Should not be reached")
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    fail("Should not be reached")
                }
            }
            every {
                identityManager.createIdentity(newUserID, callback)
            } just runs

            // when
            identityManager.identify(newUserID, callback)

            // then
            verifyOrder {
                identityManager.obtainIdentity(newUserID, any())
                identityManager.createIdentity(newUserID, callback)
            }
        }

        @Test
        fun `identify succeeds`() {
            // given
            val expectedIdentityId = "identityId"
            var resultIdentityId: String? = null
            every {
                identityManager.obtainIdentity(newUserID, capture(slotIdentityCallback))
            } answers {
                slotIdentityCallback.captured.onSuccess(expectedIdentityId)
            }

            // when
            identityManager.identify(newUserID, object : IdentityManagerCallback {
                override fun onSuccess(qonversionUserId: String) {
                    resultIdentityId = qonversionUserId
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    fail("Should not be reached")
                }
            })

            // then
            verify { identityManager.obtainIdentity(newUserID, any()) }
            assertThat(resultIdentityId).isEqualTo(expectedIdentityId)
        }

        @Test
        fun `obtain identity fails`() {
            // given
            val expectedCode = 400
            var actualError: QonversionError? = null
            var actualCode: Int? = null

            every {
                mockRepository.obtainIdentity(newUserID, any(), captureLambda())
            } answers {
                lambda<(QonversionError, Int?) -> Unit>().captured.invoke(mockError, expectedCode)
            }

            val callback = object : IdentityManagerCallback {
                override fun onSuccess(qonversionUserId: String) {
                    fail("Should not be reached")
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    actualError = error
                    actualCode = responseCode
                }
            }

            // when
            identityManager.obtainIdentity(newUserID, callback)

            // then
            verify { mockRepository.obtainIdentity(newUserID, any(), any()) }
            assertThat(actualError).isEqualTo(mockError)
            assertThat(actualCode).isEqualTo(expectedCode)
        }

        @Test
        fun `obtain identity succeeds`() {
            // given
            val expectedIdentity = "identity"

            every {
                mockRepository.obtainIdentity(newUserID, captureLambda(), any())
            } answers {
                lambda<(String) -> Unit>().captured.invoke(expectedIdentity)
            }

            val callback = mockk<IdentityManagerCallback>()

            every { identityManager.handleIdentity(callback, expectedIdentity, newUserID) } just runs

            // when
            identityManager.obtainIdentity(newUserID, callback)

            // then
            verify {
                identityManager.handleIdentity(callback, expectedIdentity, newUserID)
                mockRepository.obtainIdentity(newUserID, any(), any())
            }
        }

        @Test
        fun `create identity fails`() {
            // given
            val expectedCode = 400
            var actualError: QonversionError? = null
            var actualCode: Int? = null

            every {
                mockRepository.createIdentity(currentUserID, newUserID, any(), captureLambda())
            } answers {
                lambda<(QonversionError, Int?) -> Unit>().captured.invoke(mockError, expectedCode)
            }

            val callback = object : IdentityManagerCallback {
                override fun onSuccess(qonversionUserId: String) {
                    fail("Should not be reached")
                }

                override fun onError(error: QonversionError, responseCode: Int?) {
                    actualError = error
                    actualCode = responseCode
                }
            }

            // when
            identityManager.createIdentity(newUserID, callback)

            // then
            verify { mockRepository.createIdentity(currentUserID, newUserID, any(), any()) }
            assertThat(actualError).isEqualTo(mockError)
            assertThat(actualCode).isEqualTo(expectedCode)
        }

        @Test
        fun `create identity succeeds`() {
            // given
            val expectedIdentity = "identity"

            every {
                mockRepository.createIdentity(currentUserID, newUserID, captureLambda(), any())
            } answers {
                lambda<(String) -> Unit>().captured.invoke(expectedIdentity)
            }

            val callback = mockk<IdentityManagerCallback>()
            every {
                identityManager.handleIdentity(callback, expectedIdentity, newUserID)
            } just runs

            // when
            identityManager.createIdentity(newUserID, callback)

            // then
            verify {
                identityManager.handleIdentity(callback, expectedIdentity, newUserID)
                mockRepository.createIdentity(currentUserID, newUserID, any(), any())
            }
        }

        @Test
        fun `handle identity with non empty id`() {
            // given
            val expectedIdentityId = "identityId"
            val callback = mockk<IdentityManagerCallback>(relaxed = true)
            every { mockUserInfoService.storeQonversionUserId(expectedIdentityId) } just runs
            every { mockUserInfoService.storeCustomUserId(newUserID) } just runs

            // when
            identityManager.handleIdentity(callback, expectedIdentityId, newUserID)

            // then
            verifyOrder {
                mockUserInfoService.storeCustomUserId(newUserID)
                mockUserInfoService.storeQonversionUserId(expectedIdentityId)
                callback.onSuccess(expectedIdentityId)
            }
        }

        @Test
        fun `handle identity with empty id`() {
            // given
            val expectedIdentityId = ""
            val callback = mockk<IdentityManagerCallback>(relaxed = true)
            every { mockUserInfoService.storeQonversionUserId(expectedIdentityId) } just runs
            every { mockUserInfoService.storeCustomUserId(newUserID) } just runs

            // when
            identityManager.handleIdentity(callback, expectedIdentityId, newUserID)

            // then
            verifyOrder {
                mockUserInfoService.storeCustomUserId(newUserID)
                callback.onSuccess(expectedIdentityId)
            }
            verify(exactly = 0) { mockUserInfoService.storeQonversionUserId(expectedIdentityId) }
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
