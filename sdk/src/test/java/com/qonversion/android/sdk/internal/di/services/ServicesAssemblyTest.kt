package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import com.qonversion.android.sdk.internal.user.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ServicesAssemblyTest {
    private val mockMiscAssembly = mockk<MiscAssembly>()

    @BeforeEach
    fun setup() {
        mockkObject(ServicesAssemblyImpl)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ServicesAssemblyImpl)
    }

    @Test
    fun `init`() {
        // given
        ServicesAssemblyImpl.miscAssembly = mockk()
        val miscAssemblyAfter = mockk<MiscAssembly>()
        ServicesAssemblyImpl.init(miscAssemblyAfter)

        // when
        val result = ServicesAssemblyImpl.miscAssembly

        // then
        assertThat(result).isEqualTo(miscAssemblyAfter)
    }

    @Nested
    inner class UserPropertiesServiceTest {
        private val mockRequestConfigurator = mockk<RequestConfigurator>()
        private val mockApiInteractor = mockk<ApiInteractor>()
        private val mockUserPropertiesMapper = mockk<UserPropertiesMapper>()
        private val retryPolicySlot = slot<RetryPolicy>()

        @BeforeEach
        fun setup() {
            every {
                mockMiscAssembly.requestConfigurator
            } returns mockRequestConfigurator

            every {
                mockMiscAssembly.getApiInteractor(capture(retryPolicySlot))
            } returns mockApiInteractor

            every {
                mockMiscAssembly.userPropertiesMapper
            } returns mockUserPropertiesMapper

            every {
                ServicesAssemblyImpl.miscAssembly
            } returns mockMiscAssembly
        }

        @Test
        fun `get user properties service`() {
            // given
            val expectedResult = UserPropertiesServiceImpl(
                mockRequestConfigurator,
                mockApiInteractor,
                mockUserPropertiesMapper
            )

            // when
            val result = ServicesAssemblyImpl.userPropertiesService

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
            assertThat(retryPolicySlot.captured).isInstanceOf(RetryPolicy.InfiniteExponential::class.java)
        }

        @Test
        fun `get different user properties services`() {
            // given

            // when
            val firstResult = ServicesAssemblyImpl.userPropertiesService
            val secondResult = ServicesAssemblyImpl.userPropertiesService

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    //    return
    @Nested
    inner class UserServiceTest {
        private val mockRequestConfigurator = mockk<RequestConfigurator>()
        private val mockApiInteractor = mockk<ApiInteractor>()
        private val mockUserMapper = mockk<UserMapper>()
        private val mockLocalStorage = mockk<LocalStorage>()
        private val retryPolicySlot = slot<RetryPolicy>()

        @BeforeEach
        fun setup() {
            every {
                mockMiscAssembly.requestConfigurator
            } returns mockRequestConfigurator

            every {
                mockMiscAssembly.getApiInteractor(capture(retryPolicySlot))
            } returns mockApiInteractor

            every {
                mockMiscAssembly.userMapper
            } returns mockUserMapper

            every {
                mockMiscAssembly.localStorage
            } returns mockLocalStorage

            every {
                ServicesAssemblyImpl.miscAssembly
            } returns mockMiscAssembly
        }

        @Test
        fun `get user service`() {
            // given
            val expectedResult = UserServiceImpl(
                mockRequestConfigurator,
                mockApiInteractor,
                mockUserMapper,
                mockLocalStorage
            )

            // when
            val result = ServicesAssemblyImpl.userService

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
            assertThat(retryPolicySlot.captured).isInstanceOf(RetryPolicy.Exponential::class.java)
        }

        @Test
        fun `get different user services`() {
            // given

            // when
            val firstResult = ServicesAssemblyImpl.userService
            val secondResult = ServicesAssemblyImpl.userService

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }
}
