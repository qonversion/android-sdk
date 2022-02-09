package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.BASE_API_URL
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClientImpl
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.just
import io.mockk.Runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.random.Random

internal class MiscAssemblyTest {
    @BeforeEach
    fun setup() {
        mockkObject(MiscAssemblyImpl)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(MiscAssemblyImpl)
    }

    @Test
    fun `init and get application`() {
        // given
        MiscAssemblyImpl.application = mockk()
        val applicationAfter = mockk<Application>()
        MiscAssemblyImpl.init(applicationAfter)

        // when
        val result = MiscAssemblyImpl.application

        // then
        assertThat(result).isEqualTo(applicationAfter)
    }

    @Test
    fun `get internal config`() {
        // given

        // when
        val result = MiscAssemblyImpl.internalConfig

        // then
        assertThat(result).isEqualTo(InternalConfig)
    }

    @Nested
    inner class LoggerTest {
        private val mockInternalConfig = mockk<InternalConfig>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideInternalConfig()
            } returns mockInternalConfig
        }

        @Test
        fun `get logger`() {
            // given
            val expectedResult = ConsoleLogger(mockInternalConfig)

            // when
            val result = MiscAssemblyImpl.logger

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different loggers`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.logger
            val secondResult = MiscAssemblyImpl.logger

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class RequestConfiguratorTest {
        private val mockInternalConfig = mockk<InternalConfig>()
        private val mockHeaderBuilder = mockk<HeaderBuilderImpl>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideInternalConfig()
            } returns mockInternalConfig

            every {
                MiscAssemblyImpl.provideHeaderBuilder()
            } returns mockHeaderBuilder
        }

        @Test
        fun `get request configurator`() {
            // given
            val expectedResult = RequestConfiguratorImpl(
                mockHeaderBuilder,
                BASE_API_URL,
                mockInternalConfig,
                mockInternalConfig
            )

            // when
            val result = MiscAssemblyImpl.requestConfigurator

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different request configurators`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.requestConfigurator
            val secondResult = MiscAssemblyImpl.requestConfigurator

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class HeaderBuilderTest {
        private val mockInternalConfig = mockk<InternalConfig>()
        private val mockLocalStorage = mockk<LocalStorage>()
        private val mockLocale = mockk<Locale>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideInternalConfig()
            } returns mockInternalConfig

            every {
                MiscAssemblyImpl.provideLocalStorage()
            } returns mockLocalStorage

            every {
                MiscAssemblyImpl.provideLocale()
            } returns mockLocale
        }

        @Test
        fun `get header builder`() {
            // given
            val expectedResult = HeaderBuilderImpl(
                mockLocalStorage,
                mockLocale,
                mockInternalConfig,
                mockInternalConfig,
                mockInternalConfig
            )

            // when
            val result = MiscAssemblyImpl.headerBuilder

            // then
            assertThat(result).isEqualToComparingOnlyGivenFields(expectedResult)
        }

        @Test
        fun `get different header builders`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.headerBuilder
            val secondResult = MiscAssemblyImpl.headerBuilder

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get shared preferences`() {
        // given
        val mockApplication = mockk<Application>()
        val mockSharedPreferences = mockk<SharedPreferences>()

        every {
            mockApplication.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } returns mockSharedPreferences

        every {
            MiscAssemblyImpl.application
        } returns mockApplication

        // when
        val result = MiscAssemblyImpl.sharedPreferences

        // then
        assertThat(result).isEqualTo(mockSharedPreferences)
    }

    @Nested
    inner class LocalStorageTest {
        private val mockSharedPreferences = mockk<SharedPreferences>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideSharedPreferences()
            } returns mockSharedPreferences
        }

        @Test
        fun `get local storage`() {
            // given
            val expectedResult = SharedPreferencesStorage(mockSharedPreferences)

            // when
            val result = MiscAssemblyImpl.localStorage

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different local storages`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.localStorage
            val secondResult = MiscAssemblyImpl.localStorage

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get locale`() {
        // given
        val storedLocale = Locale.getDefault()
        val mockLocale = mockk<Locale>()
        Locale.setDefault(mockLocale)  // mock Locale

        // when
        val result = MiscAssemblyImpl.locale

        // then
        assertThat(result).isEqualTo(mockLocale)
        Locale.setDefault(storedLocale) // unmock Locale
    }

    @Nested
    inner class NetworkClientTest {
        private val mockSerializer = mockk<Serializer>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideSerializer()
            } returns mockSerializer
        }

        @Test
        fun `get network client`() {
            // given
            val expectedResult = NetworkClientImpl(mockSerializer)

            // when
            val result = MiscAssemblyImpl.networkClient

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different network clients`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.networkClient
            val secondResult = MiscAssemblyImpl.networkClient

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class SerializerTest {
        @Test
        fun `get serializer`() {
            // given

            // when
            val result = MiscAssemblyImpl.serializer

            // then
            assertThat(result).isInstanceOf(JsonSerializer::class.java)
        }

        @Test
        fun `get different serializers`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.serializer
            val secondResult = MiscAssemblyImpl.serializer

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class DelayCalculatorTest {
        @Test
        fun `get delay calculator`() {
            // given
            val expectedResult = ExponentialDelayCalculator(Random)

            // when
            val result = MiscAssemblyImpl.delayCalculator

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
            assertThat(result).isInstanceOf(ExponentialDelayCalculator::class.java)
        }

        @Test
        fun `get different delay calculators`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.delayCalculator
            val secondResult = MiscAssemblyImpl.delayCalculator

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class ErrorResponseMapperTest {
        @Test
        fun `get error response mapper`() {
            // given

            // when
            val result = MiscAssemblyImpl.errorResponseMapper

            // then
            assertThat(result).isInstanceOf(ApiErrorMapper::class.java)
        }

        @Test
        fun `get different error response mappers`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.errorResponseMapper
            val secondResult = MiscAssemblyImpl.errorResponseMapper

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class AppLifecycleObserverTest {
        private val lifecycleObserverSlot = slot<AppLifecycleObserverImpl>()

        @BeforeEach
        fun setUp() {
            val mockApplication = mockk<Application>()

            every {
                mockApplication.registerActivityLifecycleCallbacks(capture(lifecycleObserverSlot))
            } just Runs

            every {
                MiscAssemblyImpl.application
            } returns mockApplication
        }

        @Test
        fun `get app lifecycle observer`() {
            // given

            // when
            val result = MiscAssemblyImpl.appLifecycleObserver

            // then
            assertThat(result).isEqualTo(lifecycleObserverSlot.captured)
        }

        @Test
        fun `get different app lifecycle observers`() {
            // given and when
            val firstResult = MiscAssemblyImpl.appLifecycleObserver
            val secondResult = MiscAssemblyImpl.appLifecycleObserver

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GetApiInteractorTest {
        private val mockNetworkClient = mockk<NetworkClient>()
        private val mockCalculator = mockk<RetryDelayCalculator>()
        private val mockInternalConfig = mockk<InternalConfig>()
        private val mockErrorResponseMapper = mockk<ErrorResponseMapper>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideInternalConfig()
            } returns mockInternalConfig

            every {
                MiscAssemblyImpl.provideNetworkClient()
            } returns mockNetworkClient

            every {
                MiscAssemblyImpl.provideDelayCalculator()
            } returns mockCalculator

            every {
                MiscAssemblyImpl.provideErrorResponseMapper()
            } returns mockErrorResponseMapper
        }

        @Test
        fun `get api interactor with possible retry policy`() {
            // given
            val retryPolicy = listOf(RetryPolicy.Exponential(), RetryPolicy.InfiniteExponential())
            retryPolicy.forEach { policy ->
                val expectedResult = ApiInteractorImpl(
                    mockNetworkClient,
                    mockCalculator,
                    mockInternalConfig,
                    mockErrorResponseMapper,
                    policy
                )
                // when
                val result = MiscAssemblyImpl.getApiInteractor(policy)

                // then
                assertThat(result).isEqualToComparingFieldByField(expectedResult)
            }
        }

        @Test
        fun `get different api interactors`() {
            // given
            val mockRetryPolicy = mockk<RetryPolicy>()

            // when
            val firstResult = MiscAssemblyImpl.getApiInteractor(mockRetryPolicy)
            val secondResult = MiscAssemblyImpl.getApiInteractor(mockRetryPolicy)

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }


    @Nested
    inner class UserMapperTest {
        private val mockUserPurchaseMapper = mockk<UserPurchaseMapper>()
        private val mockEntitlementMapper = mockk<EntitlementMapper>()

        @BeforeEach
        fun setup() {
            every {
                MiscAssemblyImpl.provideUserPurchaseMapper()
            } returns mockUserPurchaseMapper

            every {
                MiscAssemblyImpl.provideEntitlementMapper()
            } returns mockEntitlementMapper
        }

        @Test
        fun `get user mapper`() {
            // given
            val expectedResult = UserMapper(mockUserPurchaseMapper, mockEntitlementMapper)

            // when
            val result = MiscAssemblyImpl.userMapper

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user mappers`() {
            // given

            // when
            val firstResult = MiscAssemblyImpl.userMapper
            val secondResult = MiscAssemblyImpl.userMapper

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get different product mappers`() {
        // given

        // when
        val firstResult = MiscAssemblyImpl.productMapper
        val secondResult = MiscAssemblyImpl.productMapper

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different subscription mappers`() {
        // given

        // when
        val firstResult = MiscAssemblyImpl.subscriptionMapper
        val secondResult = MiscAssemblyImpl.subscriptionMapper

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different entitlement mappers`() {
        // given

        // when
        val firstResult = MiscAssemblyImpl.entitlementMapper
        val secondResult = MiscAssemblyImpl.entitlementMapper

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different user properties mappers`() {
        // given

        // when
        val firstResult = MiscAssemblyImpl.userPropertiesMapper
        val secondResult = MiscAssemblyImpl.userPropertiesMapper

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different user purchase mappers`() {
        // given

        // when
        val firstResult = MiscAssemblyImpl.userPurchaseMapper
        val secondResult = MiscAssemblyImpl.userPurchaseMapper

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }
}
