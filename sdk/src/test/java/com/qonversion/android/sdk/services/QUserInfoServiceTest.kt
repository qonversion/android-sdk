package com.qonversion.android.sdk.services

import com.qonversion.android.sdk.storage.SharedPreferencesCache
import com.qonversion.android.sdk.storage.TokenStorage
import io.mockk.*
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

    private lateinit var userInfoService: QUserInfoService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        userInfoService = QUserInfoService(mockSharedPreferencesCache, mockTokenStorage)
    }

    @Nested
    inner class ObtainUserID {
        @Test
        fun `should load UID from storage when preferences is empty`() {
            // given
            val userID = ""
            val token = "token"

            every {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
            } returns userID

            every {
                mockTokenStorage.load()
            } returns token

            // when
            val resultId = userInfoService.obtainUserID()

            // then
            verify(exactly = 1) {
                mockTokenStorage.load()
                mockTokenStorage.delete()
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, token)
                mockSharedPreferencesCache.putString(prefsUserIdKey, token)
            }

            Assertions.assertThat(resultId).isEqualTo(token)
        }

        @Test
        fun `should generate UID when preferences and storage are empty`() {
            // given
            val userID = ""
            val token = ""
            val randomUID = "08111735c1a641f085cae9d0ab98a642"
            val generatedUID = "QON_$randomUID"

            every {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
            } returns userID

            every {
                mockTokenStorage.load()
            } returns token

            mockkStatic(UUID::class)
            every {
                UUID.randomUUID().toString()
            } returns randomUID

            // when
            val resultId = userInfoService.obtainUserID()

            // then
            verify(exactly = 1) {
                mockTokenStorage.load()
                mockTokenStorage.delete()
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, generatedUID)
                mockSharedPreferencesCache.putString(prefsUserIdKey, generatedUID)
            }

            Assertions.assertThat(resultId).isEqualTo(generatedUID)
        }

        @Test
        fun `should load UID from preferences when it is not empty`() {
            // given
            val userID = "userID"

            every {
                mockSharedPreferencesCache.getString(prefsUserIdKey, null)
            } returns userID

            // when
            val resultId = userInfoService.obtainUserID()

            // then
            verify(exactly = 0) {
                mockTokenStorage.load()
                mockTokenStorage.delete()
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, any())
                mockSharedPreferencesCache.putString(prefsUserIdKey, any())
            }

            Assertions.assertThat(resultId).isEqualTo(userID)
        }
    }

    @Nested
    inner class StoreIdentity {
        @Test
        fun `should store userID to preferences`() {
            // given
            val userID = "userID"

            // when
            userInfoService.storeIdentity(userID)

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsUserIdKey, userID)
            }
        }
    }

    @Nested
    inner class Logout {
        @Test
        fun `should store original userID to preferences`() {
            // given
            val originalUserID = "originalUserID"

            every {
                mockSharedPreferencesCache.getString(prefsOriginalUserIdKey, null)
            } returns originalUserID

            // when
            userInfoService.logout()

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsUserIdKey, originalUserID)
            }
        }
    }

    @Nested
    inner class DeleteUser {
        @Test
        fun `should empty preferences and storage`() {
            // given

            // when
            userInfoService.deleteUser()

            // then
            verify(exactly = 1) {
                mockSharedPreferencesCache.putString(prefsUserIdKey, null)
                mockSharedPreferencesCache.putString(prefsOriginalUserIdKey, null)
                mockTokenStorage.delete()
            }
        }
    }
}