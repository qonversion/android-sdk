package com.qonversion.android.sdk.services

import com.qonversion.android.sdk.storage.SharedPreferencesCache
import com.qonversion.android.sdk.storage.TokenStorage
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

    private val prefsQonversionUserIdKey = "com.qonversion.keys.storedUserID"
    private val prefsCustomUserIdKey = "com.qonversion.keys.customUserID"
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
                mockSharedPreferencesCache.getString(prefsQonversionUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, generatedUID)
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
                mockSharedPreferencesCache.getString(prefsQonversionUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, generatedUID)
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
                mockSharedPreferencesCache.getString(prefsQonversionUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, token)
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
                mockSharedPreferencesCache.getString(prefsQonversionUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, token)
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
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, any())
            }
        }

        private fun mockUserIDCache(userID: String?) {
            every {
                mockSharedPreferencesCache.getString(prefsQonversionUserIdKey, null)
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
    fun storeQonversionUserId() {
        // given
        val userID = "qonversionUserID"

        // when
        userInfoService.storeQonversionUserId(userID)

        // then
        verify(exactly = 1) {
            mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, userID)
        }
    }

    @Test
    fun storeCustomUserId() {
        // given
        val userID = "customUserID"

        // when
        userInfoService.storeCustomUserId(userID)

        // then
        verify(exactly = 1) {
            mockSharedPreferencesCache.putString(prefsCustomUserIdKey, userID)
        }
    }

    @Test
    fun `should not logout if custom user id was not set`() {
        // given
        every {
            mockSharedPreferencesCache.getString(prefsCustomUserIdKey, null)
        } returns null

        // when
        val isLogoutNeeded = userInfoService.logoutIfNeeded()

        // then
        verify {
            mockSharedPreferencesCache.getString(prefsCustomUserIdKey, null)
        }

        verify(exactly = 0) {
            mockSharedPreferencesCache.putString(any(), any())
            mockSharedPreferencesCache.remove(any())
        }

        assertEquals("must be false", false, isLogoutNeeded)
    }

    @Test
    fun `should logout if custom user id was set`() {
        // given
        userInfoService = spyk(userInfoService)
        val customUserId = "customUserID"
        val newQonversionUserId = "newQonversionUserId"

        every {
            mockSharedPreferencesCache.getString(prefsCustomUserIdKey, null)
        } returns customUserId

        every { userInfoService.generateRandomUserID() } returns newQonversionUserId

        // when
        val isLogoutNeeded = userInfoService.logoutIfNeeded()

        // then
        verifyOrder {
            mockSharedPreferencesCache.getString(prefsCustomUserIdKey, null)
            mockSharedPreferencesCache.remove(prefsCustomUserIdKey)
            userInfoService.generateRandomUserID()
            mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, newQonversionUserId)
        }

        assertEquals("must be true", true, isLogoutNeeded)
    }

    @Nested
    inner class DeleteUser {
        @Test
        fun `delete user`() {
            // when
            userInfoService.deleteUser()

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsQonversionUserIdKey, null)
                mockTokenStorage.delete()
            }
        }
    }
}