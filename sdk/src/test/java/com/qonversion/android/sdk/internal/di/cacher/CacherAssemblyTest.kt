package com.qonversion.android.sdk.internal.di.cacher

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.cache.CacherImpl
import com.qonversion.android.sdk.internal.cache.mapper.CacheMapper
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.storage.UserDataProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class CacherAssemblyTest {
    private lateinit var cacherAssemblyImpl: CacherAssemblyImpl

    private val mockMiscAssembly = mockk<MiscAssembly>()
    private val mockInternalConfig = mockk<InternalConfig>()
    private val mockStorageAssembly = mockk<StorageAssembly>()
    private val mockMappersAssembly = mockk<MappersAssembly>()

    @BeforeEach
    fun setup() {
        cacherAssemblyImpl =
            CacherAssemblyImpl(
                mockStorageAssembly,
                mockMappersAssembly,
                mockMiscAssembly,
                mockInternalConfig
            )
    }

    @Nested
    inner class UserCacherTest {
        private val mockUserDataProvider = mockk<UserDataProvider>()
        private val mockUserCacheMapper = mockk<CacheMapper<User?>>()
        private val mockSharedPreferencesStorage = mockk<SharedPreferencesStorage>()
        private val mockAppLifecycleObserver = mockk<AppLifecycleObserver>()
        private val mockLogger = mockk<Logger>()

        @BeforeEach
        fun setUp() {

            every {
                mockStorageAssembly.userDataProvider()
            } returns mockUserDataProvider

            every {
                mockMappersAssembly.userCacheMapper()
            } returns mockUserCacheMapper

            every {
                mockStorageAssembly.sharedPreferencesStorage()
            } returns mockSharedPreferencesStorage

            every {
                mockMiscAssembly.appLifecycleObserver()
            } returns mockAppLifecycleObserver

            every {
                mockMiscAssembly.logger()
            } returns mockLogger
        }

        @Test
        fun `get user cacher`() {
            // given
            val expectedResult = CacherImpl(
                StorageConstants.UserInfo.key,
                mockUserDataProvider,
                mockUserCacheMapper,
                mockSharedPreferencesStorage,
                mockAppLifecycleObserver,
                mockInternalConfig,
                mockLogger
            )
            // when
            val result = cacherAssemblyImpl.userCacher()

            // then
            assertThat(result).isInstanceOf(CacherImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user cachers`() {
            // given

            // when
            val firstResult = cacherAssemblyImpl.userCacher()
            val secondResult = cacherAssemblyImpl.userCacher()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}