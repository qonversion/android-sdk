package com.qonversion.android.sdk.internal.di.network

import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.common.BASE_API_URL
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.error.ErrorResponseMapper
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.di.mappers.MappersAssembly
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.networkLayer.RetryPolicy
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractorImpl
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilderImpl
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClient
import com.qonversion.android.sdk.internal.networkLayer.networkClient.NetworkClientImpl
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfiguratorImpl
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale

internal class NetworkAssemblyTest {
    private lateinit var networkAssembly: NetworkAssemblyImpl
    private val mockMappersAssembly = mockk<MappersAssembly>()
    private val mockMiscAssembly = mockk<MiscAssembly>()
    private val mockStorageAssembly = mockk<StorageAssembly>()
    private val mockInternalConfig = mockk<InternalConfig>()

    @BeforeEach
    fun setUp() {
        networkAssembly =
            NetworkAssemblyImpl(
                mockInternalConfig,
                mockMappersAssembly,
                mockStorageAssembly,
                mockMiscAssembly
            )
    }

    @Nested
    inner class NetworkClientTest {
        private val mockSerializer = mockk<Serializer>()

        @BeforeEach
        fun setup() {
            every {
                mockMiscAssembly.jsonSerializer()
            } returns mockSerializer
        }

        @Test
        fun `get network client`() {
            // given
            val expectedResult = NetworkClientImpl(mockSerializer)

            // when
            val result = networkAssembly.networkClient()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different network clients`() {
            // given

            // when
            val firstResult = networkAssembly.networkClient()
            val secondResult = networkAssembly.networkClient()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class RequestConfiguratorTest {
        private val mockHeaderBuilder = mockk<HeaderBuilderImpl>()

        @BeforeEach
        fun setup() {
            networkAssembly = spyk(networkAssembly)

            every {
                networkAssembly.headerBuilder()
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
            val result = networkAssembly.requestConfigurator()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different request configurators`() {
            // given

            // when
            val firstResult = networkAssembly.requestConfigurator()
            val secondResult = networkAssembly.requestConfigurator()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GetApiInteractorTest {
        private val mockNetworkClient = mockk<NetworkClient>()
        private val mockCalculator = mockk<RetryDelayCalculator>()
        private val mockApiResponseMapper = mockk<ErrorResponseMapper>()

        @BeforeEach
        fun setup() {
            networkAssembly = spyk(networkAssembly)

            every {
                networkAssembly.networkClient()
            } returns mockNetworkClient

            every {
                mockMiscAssembly.exponentialDelayCalculator()
            } returns mockCalculator

            every {
                mockMappersAssembly.apiErrorMapper()
            } returns mockApiResponseMapper
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
                    mockApiResponseMapper,
                    policy
                )
                // when
                val result = networkAssembly.provideApiInteractor(policy)

                // then
                assertThat(result).isEqualToComparingFieldByField(expectedResult)
            }
        }

        @Test
        fun `get exponential api interactor`() {
            // given

            val mockApiInteractor = mockk<ApiInteractor>()

            every {
                networkAssembly.exponentialApiInteractor()
            } returns mockApiInteractor

            // when
            val result = networkAssembly.exponentialApiInteractor()

            // then

            assertThat(result).isSameAs(mockApiInteractor)
        }

        @Test
        fun `get infinite exponential api interactor`() {
            // given

            val mockApiInteractor = mockk<ApiInteractor>()

            every {
                networkAssembly.infiniteExponentialApiInteractor()
            } returns mockApiInteractor

            // when
            val result = networkAssembly.infiniteExponentialApiInteractor()

            // then

            assertThat(result).isSameAs(mockApiInteractor)
        }

        @Test
        fun `get different exponential api interactors`() {
            // given

            // when
            val firstResult = networkAssembly.exponentialApiInteractor()
            val secondResult = networkAssembly.exponentialApiInteractor()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }

        @Test
        fun `get different infinite exponential api interactors`() {
            // given

            // when
            val firstResult = networkAssembly.infiniteExponentialApiInteractor()
            val secondResult = networkAssembly.infiniteExponentialApiInteractor()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class HeaderBuilderTest {
        private val mockLocalStorage = mockk<LocalStorage>()
        private val mockLocale = mockk<Locale>()

        @BeforeEach
        fun setup() {
            every {
                mockStorageAssembly.sharedPreferencesStorage()
            } returns mockLocalStorage

            every {
                mockMiscAssembly.locale()
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
            val result = networkAssembly.headerBuilder()

            // then
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult, "localStorage", "locale",
                "primaryConfigProvider",
                "environmentProvider",
                "uidProvider"
            )
        }

        @Test
        fun `get different header builders`() {
            // given

            // when
            val firstResult = networkAssembly.headerBuilder()
            val secondResult = networkAssembly.headerBuilder()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }
}
