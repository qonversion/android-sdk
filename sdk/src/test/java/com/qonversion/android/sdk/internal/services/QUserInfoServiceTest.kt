package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.storage.SharedPreferencesCache
import com.qonversion.android.sdk.internal.storage.TokenStorage
import io.mockk.*
import org.junit.Assert.assertEquals
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class QUserInfoServiceTest {
    private val mockSharedPreferencesCache = mockk<SharedPreferencesCache>(relaxed = true)
    private val mockTokenStorage = mockk<TokenStorage>(relaxed = true)

    private val prefsOriginalUserIdKey = "com.qonversion.keys.originalUserID"
    private val prefsUserIdKey = "com.qonversion.keys.storedUserID"
    private val prefsPartnerIdentityUserIdKey = "com.qonversion.keys.partnerIdentityUserID"
    private val randomUID = "08111735c1a641f085cae9d0ab98a642"
    private val generatedUID = "QON_$randomUID"

    private lateinit var userInfoService: QUserInfoService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        userInfoService = QUserInfoService(mockSharedPreferencesCache, mockTokenStorage)
    }

    @Nested
    inner class ObtainUserID {
        @Test
        fun `should load UID from token storage when UID from preferences is empty`() {
            // given
            val userID = ""
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockTokenStorage.load()
                mockTokenStorage.delete()
            }
        }

        @Test
        fun `should load and delete UID from token storage when UID from preferences is null`() {
            // given
            val userID = null
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockTokenStorage.load()
                mockTokenStorage.delete()
            }
        }

        @Test
        fun `should not load UID from token storage when UID from preferences is not empty`() {
            // given
            val userID = "userID"
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verify(exactly = 0) {
                mockTokenStorage.load()
            }
        }

        @Test
        fun `should not delete UID from token storage when UID from preferences is not empty`() {
            // given
            val userID = "userID"
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verify(exactly = 0) {
                mockTokenStorage.delete()
            }
        }

        @Test
        fun `should generate random UID when UID from token storage is empty`() {
            // given
            val userID = ""
            val token = ""
            mockUserIDCache(userID)
            mockTokenStorage(token)
            mockUUID()

            // when
            val resultID = userInfoService.obtainUserID()

            // then
            Assertions.assertThat(resultID).isEqualTo(generatedUID)
        }

        @Test
        fun `should not generate random UID when UID from token storage is not empty`() {
            // given
            val userID = ""
            val token = "token"
            mockUserIDCache(userID)
            mockTokenStorage(token)

            // when
            val resultId = userInfoService.obtainUserID()

            // then
            Assertions.assertThat(resultId).isEqualTo(token)
        }

        @Test
        fun `should save generated UID to preferences when UID from preferences is empty`() {
            // given
            val userID = ""
            val token = ""
            mockUserIDCache(userID)
            mockTokenStorage(token)
            mockUUID()

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsUserIdKey, generatedUID)
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, generatedUID)
            }
        }

        @Test
        fun `should save generated UID to preferences when UID from preferences is null`() {
            // given
            val userID = null
            val token = ""
            mockUserIDCache(userID)
            mockTokenStorage(token)
            mockUUID()

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsUserIdKey, generatedUID)
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, generatedUID)
            }
        }

        @Test
        fun `should save UID from token storage to preferences when UID from preferences is empty`() {
            // given
            val userID = ""
            val token = "token"
            mockUserIDCache(userID)
            mockTokenStorage(token)

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsUserIdKey, token)
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, token)
            }
        }

        @Test
        fun `should save UID from token storage to preferences when UID from preferences is null`() {
            // given
            val userID = null
            val token = "token"
            mockUserIDCache(userID)
            mockTokenStorage(token)

            // when
            userInfoService.obtainUserID()

            // then
            verifySequence {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsUserIdKey, token)
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, token)
            }
        }

        @Test
        fun `should not save UID as storedUserID when UID from preferences is not empty`() {
            // given
            val userID = "userID"
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verify(exactly = 0) {
                mockSharedPreferencesCache.putString(prefsUserIdKey, any())
            }
        }

        @Test
        fun `should not save UID as originalUserID when UID from preferences is not empty`() {
            // given
            val userID = "userID"
            mockUserIDCache(userID)

            // when
            userInfoService.obtainUserID()

            // then
            verify(exactly = 0) {
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, any())
            }
        }

        private fun mockUserIDCache(userID: String?) {
            every {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
            } returns userID
        }

        private fun mockTokenStorage(token: String) {
            every {
                mockTokenStorage.load()
            } returns token
        }

        private fun mockUUID() {
            mockkStatic(UUID::class)
            every {
                UUID.randomUUID().toString()
            } returns randomUID
        }
    }

    @Test
    fun storeIdentity() {
        // given
        val userID = "userID"

        // when
        userInfoService.storeQonversionUserId(userID)

        // then
        verify(exactly = 1) {
            mockSharedPreferencesCache.putString(prefsUserIdKey, userID)
        }
    }

    @Test
    fun `should not logout if logged user ID is same as anon`() {
        // given
        val originalUserID = "originalUserID"

        every {
            mockSharedPreferencesCache.getString(prefsOriginalUserIdKey, null)
        } returns originalUserID

        every {
            mockSharedPreferencesCache.getString(prefsUserIdKey, null)
        } returns originalUserID

        // when
        val isLogoutNeeded = userInfoService.logoutIfNeeded()

        // then
        verifySequence {
            mockSharedPreferencesCache.getString(prefsOriginalUserIdKey, null)
            mockSharedPreferencesCache.getString(prefsUserIdKey, null)
        }

        verify(exactly = 0) {
            mockSharedPreferencesCache.putString(any(), any())
        }

        assertEquals("must be false", false, isLogoutNeeded)
    }

    @Test
    fun `should logout if logged user ID is not same as anon`() {
        // given
        val originalUserID = "originalUserID"
        val userID = "userID"

        every {
            mockSharedPreferencesCache.getString(prefsOriginalUserIdKey, null)
        } returns originalUserID

        every {
            mockSharedPreferencesCache.getString(prefsUserIdKey, null)
        } returns userID

        // when
        val isLogoutNeeded = userInfoService.logoutIfNeeded()

        // then
        verifySequence {
            mockSharedPreferencesCache.getString(prefsOriginalUserIdKey, null)
            mockSharedPreferencesCache.getString(prefsUserIdKey, null)
            mockSharedPreferencesCache.putString(prefsUserIdKey, originalUserID)
            mockSharedPreferencesCache.putString(prefsPartnerIdentityUserIdKey, null)
        }

        assertEquals("must be true", true, isLogoutNeeded)
    }

    @Nested
    inner class DeleteUser {
        @Test
        fun `should empty preferences from originalUserID`() {
            // when
            userInfoService.deleteUser()

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, null)
            }
        }

        @Test
        fun `should empty preferences from storedUserID`() {
            // when
            userInfoService.deleteUser()

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsUserIdKey, null)
            }
        }

        @Test
        fun `should empty token storage`() {
            // when
            userInfoService.deleteUser()

            // then
            verify(exactly = 1) {
                mockTokenStorage.delete()
            }
        }
    }
}