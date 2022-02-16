package com.qonversion.android.sdk.internal.di.storage

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import com.qonversion.android.sdk.internal.user.storage.UserDataStorageImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorageImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class StorageAssemblyTest {
    lateinit var storageAssembly: StorageAssemblyImpl
    private val mockMappersAssembly = mockk<MappersAssembly>()
    private val mockMiscAssembly = mockk<MiscAssembly>()
    private val mockApplication = mockk<Application>()

    @BeforeEach
    fun setUp() {
        storageAssembly = spyk(StorageAssemblyImpl(mockApplication, mockMappersAssembly, mockMiscAssembly))
    }

    @Test
    fun `get shared preferences`() {
        // given
        val mockSharedPreferences = mockk<SharedPreferences>()

        every {
            mockApplication.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } returns mockSharedPreferences

        // when
        val result = storageAssembly.sharedPreferences()

        // then
        assertThat(result).isEqualTo(mockSharedPreferences)
    }

    @Nested
    inner class SharedPreferencesStorageTest {
        private val mockSharedPreferences = mockk<SharedPreferences>()

        @BeforeEach
        fun setup() {
            every {
                storageAssembly.sharedPreferences()
            } returns mockSharedPreferences
        }

        @Test
        fun `get local storage`() {
            // given
            val expectedResult = SharedPreferencesStorage(mockSharedPreferences)

            // when
            val result = storageAssembly.sharedPreferencesStorage()

            // then
            assertThat(result).isInstanceOf(SharedPreferencesStorage::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different local storages`() {
            // given

            // when
            val firstResult = storageAssembly.sharedPreferencesStorage()
            val secondResult = storageAssembly.sharedPreferencesStorage()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class PropertiesStorageTest {
        private val mockLocaleStorage = mockk<LocalStorage>()
        private val mockMapDataMapper = mockk<MapDataMapper>()
        private val mockLogger = mockk<Logger>()

        @BeforeEach
        fun setup() {
            every {
                storageAssembly.sharedPreferencesStorage()
            } returns mockLocaleStorage

            every {
                mockMappersAssembly.mapDataMapper()
            } returns mockMapDataMapper

            every {
                mockMiscAssembly.logger()
            } returns mockLogger
        }

        @Test
        fun `get sent user properties storage`() {
            // given
            val expectedResult = UserPropertiesStorageImpl(
                mockLocaleStorage,
                mockMapDataMapper,
                StorageConstants.SentUserProperties.key,
                mockLogger
            )

            // when
            val result = storageAssembly.sentUserPropertiesStorage()

            // then
            assertThat(result).isInstanceOf(UserPropertiesStorageImpl::class.java)
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult,
                "localStorage",
                "mapper",
                "key",
                "logger"
            )
        }

        @Test
        fun `get pending user properties storage`() {
            // given
            val expectedResult = UserPropertiesStorageImpl(
                mockLocaleStorage,
                mockMapDataMapper,
                StorageConstants.PendingUserProperties.key,
                mockLogger
            )

            // when
            val result = storageAssembly.pendingUserPropertiesStorage()

            // then
            assertThat(result).isInstanceOf(UserPropertiesStorageImpl::class.java)
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult,
                "localStorage",
                "mapper",
                "key",
                "logger"
            )
        }

        @Test
        fun `get different sent user properties storages`() {
            // given

            // when
            val firstResult = storageAssembly.sentUserPropertiesStorage()
            val secondResult = storageAssembly.sentUserPropertiesStorage()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }

        @Test
        fun `get different pending user properties storages`() {
            // given

            // when
            val firstResult = storageAssembly.pendingUserPropertiesStorage()
            val secondResult = storageAssembly.pendingUserPropertiesStorage()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserDataProviderTest {
        private val mockUserDataStorage = mockk<UserDataStorage>()

        @Test
        fun `get user data provider`() {
            // given
            every { storageAssembly.userDataStorage() } returns mockUserDataStorage

            // when
            val result = storageAssembly.userDataProvider()

            // then
            assertThat(result).isSameAs(mockUserDataStorage)
            verify { storageAssembly.userDataStorage() }
        }
    }

    @Nested
    inner class UserDataStorageTest {
        private val mockLocalStorage = mockk<LocalStorage>(relaxed = true)

        @BeforeEach
        fun setup() {
            every {
                storageAssembly.sharedPreferencesStorage()
            } returns mockLocalStorage
        }

        @Test
        fun `get user data storage`() {
            // given
            val expectedResult = UserDataStorageImpl(mockLocalStorage)

            // when
            val result = storageAssembly.userDataStorage()

            // then
            assertThat(result).isInstanceOf(UserDataStorageImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user data storages`() {
            // given

            // when
            val firstResult = storageAssembly.userDataStorage()
            val secondResult = storageAssembly.userDataStorage()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}
