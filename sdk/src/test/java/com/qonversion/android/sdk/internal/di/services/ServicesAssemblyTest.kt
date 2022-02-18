package com.qonversion.android.sdk.internal.di.services

import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.network.NetworkAssembly
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.service.UserServiceDecorator
import com.qonversion.android.sdk.internal.user.service.UserServiceImpl
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesServiceImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ServicesAssemblyTest {
    private lateinit var servicesAssembly: ServicesAssembly
    private val mockNetworkAssembly = mockk<NetworkAssembly>()
    private val mockMappersAssembly = mockk<MappersAssembly>()

    @BeforeEach
    fun setup() {
        servicesAssembly =
            ServicesAssemblyImpl(mockMappersAssembly, mockNetworkAssembly)
    }

    @Nested
    inner class UserPropertiesServiceTest {
        private val mockRequestConfigurator = mockk<RequestConfigurator>()
        private val mockApiInteractor = mockk<ApiInteractor>()
        private val mockUserPropertiesMapper = mockk<UserPropertiesMapper>()

        @BeforeEach
        fun setup() {
            every {
                mockNetworkAssembly.requestConfigurator()
            } returns mockRequestConfigurator

            every {
                mockNetworkAssembly.infiniteExponentialApiInteractor()
            } returns mockApiInteractor

            every {
                mockMappersAssembly.userPropertiesMapper()
            } returns mockUserPropertiesMapper
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
            val result = servicesAssembly.userPropertiesService()

            // then
            assertThat(result).isInstanceOf(UserPropertiesServiceImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user properties services`() {
            // given

            // when
            val firstResult = servicesAssembly.userPropertiesService()
            val secondResult = servicesAssembly.userPropertiesService()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserServiceTest {
        private val mockRequestConfigurator = mockk<RequestConfigurator>()
        private val mockApiInteractor = mockk<ApiInteractor>()
        private val mockUserMapper = mockk<UserMapper>()

        @BeforeEach
        fun setup() {
            every {
                mockNetworkAssembly.requestConfigurator()
            } returns mockRequestConfigurator

            every {
                mockNetworkAssembly.exponentialApiInteractor()
            } returns mockApiInteractor

            every {
                mockMappersAssembly.userMapper()
            } returns mockUserMapper
        }

        @Test
        fun `get user service`() {
            // given
            val expectedResult = UserServiceImpl(
                mockRequestConfigurator,
                mockApiInteractor,
                mockUserMapper
            )

            // when
            val result = servicesAssembly.userService()

            // then
            assertThat(result).isInstanceOf(UserServiceImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user services`() {
            // given

            // when
            val firstResult = servicesAssembly.userService()
            val secondResult = servicesAssembly.userService()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserServiceDecoratorTest {
        private val mockUserService = mockk<UserService>()

        @BeforeEach
        fun setup() {
            servicesAssembly = spyk(servicesAssembly)
            every { servicesAssembly.userService() } returns mockUserService
        }

        @Test
        fun `get user service decorator`() {
            // given
            val expectedResult = UserServiceDecorator(mockUserService)

            // when
            val result = servicesAssembly.userServiceDecorator()

            // then
            assertThat(result).isInstanceOf(UserServiceDecorator::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user service decorators`() {
            // given

            // when
            val firstResult = servicesAssembly.userServiceDecorator()
            val secondResult = servicesAssembly.userServiceDecorator()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}
