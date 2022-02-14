package com.qonversion.android.sdk.internal.user.storage

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.ErrorCode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UserDataStorageTest {

    private val mockLocalStorage = mockk<LocalStorage>()
    private lateinit var userDataStorage: UserDataStorageImpl

    @BeforeEach
    fun setUp() {
        every { mockLocalStorage.getString(any()) } returns null
        userDataStorage = UserDataStorageImpl(mockLocalStorage)
    }

    @Test
    fun `initialization from storage`() {
        // given
        val testOriginalId = "test original id"
        val testIdentityId = "test identity id"
        every { mockLocalStorage.getString(StorageConstants.OriginalUserId.key) } returns testOriginalId
        every { mockLocalStorage.getString(StorageConstants.IdentityUserId.key) } returns testIdentityId

        // when
        val userDataStorage = UserDataStorageImpl(mockLocalStorage)

        // then
        assertThat(userDataStorage.originalId).isEqualTo(testOriginalId)
        assertThat(userDataStorage.identityId).isEqualTo(testIdentityId)
    }

    @Nested
    inner class GetUserIdTest {

        @Test
        fun `identity id exists`() {
            // given
            val testIdentityId = "test identity id"
            userDataStorage.identityId = testIdentityId

            // when
            val result = userDataStorage.getUserId()

            // then
            assertThat(result).isEqualTo(testIdentityId)
        }

        @Test
        fun `only original id exists`() {
            // given
            val testOriginalId = "test original id"
            userDataStorage.identityId = null
            userDataStorage.originalId = testOriginalId

            // when
            val result = userDataStorage.getUserId()

            // then
            assertThat(result).isEqualTo(testOriginalId)
        }

        @Test
        fun `no user id exists`() {
            // given
            userDataStorage.identityId = null
            userDataStorage.originalId = null

            // when
            val result = userDataStorage.getUserId()

            // then
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class RequireUserIdTest {

        private lateinit var spyUserDataStorage: UserDataStorage

        @BeforeEach
        fun setUp() {
            spyUserDataStorage = spyk(userDataStorage)
        }

        @Test
        fun `user id exists`() {
            // given
            val testId = "test id"
            every { spyUserDataStorage.getUserId() } returns testId

            // when
            val result = spyUserDataStorage.requireUserId()

            // then
            assertThat(result).isEqualTo(testId)
        }

        @Test
        fun `user id does not exist`() {
            // given
            every { spyUserDataStorage.getUserId() } returns null

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.UserNotFound) {
                spyUserDataStorage.requireUserId()
            }
        }
    }

    @Test
    fun `set original user id`() {
        // given
        clearMocks(mockLocalStorage)
        val testId = "test id"
        every { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, testId) } just runs
        userDataStorage.originalId = null

        // when
        userDataStorage.setOriginalUserId(testId)

        // then
        assertThat(userDataStorage.originalId).isEqualTo(testId)
        verify { mockLocalStorage.putString(StorageConstants.OriginalUserId.key, testId) }
    }

    @Test
    fun `set identity user id`() {
        // given
        clearMocks(mockLocalStorage)
        val testId = "test id"
        every { mockLocalStorage.putString(StorageConstants.IdentityUserId.key, testId) } just runs
        userDataStorage.identityId = null

        // when
        userDataStorage.setIdentityUserId(testId)

        // then
        assertThat(userDataStorage.identityId).isEqualTo(testId)
        verify { mockLocalStorage.putString(StorageConstants.IdentityUserId.key, testId) }
    }

    @Test
    fun `clear identity user id`() {
        // given
        clearMocks(mockLocalStorage)
        every { mockLocalStorage.remove(StorageConstants.IdentityUserId.key) } just runs
        userDataStorage.identityId = "test id"

        // when
        userDataStorage.clearIdentityUserId()

        // then
        assertThat(userDataStorage.identityId).isNull()
        verify { mockLocalStorage.remove(StorageConstants.IdentityUserId.key) }
    }
}
