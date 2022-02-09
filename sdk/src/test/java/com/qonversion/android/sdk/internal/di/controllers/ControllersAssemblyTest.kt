package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorageImpl
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesControllerImpl
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorkerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested

import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

internal class ControllersAssemblyTest {

    private val mockMiscAssembly = mockk<MiscAssembly>()
    private val mockServicesAssembly = mockk<ServicesAssembly>()
    private val mockLogger = mockk<Logger>()

    @BeforeEach
    fun setup() {
        mockkObject(ControllersAssemblyImpl)

        every {
            mockMiscAssembly.logger
        } returns mockLogger

        ControllersAssemblyImpl.miscAssembly = mockMiscAssembly
        ControllersAssemblyImpl.servicesAssembly = mockServicesAssembly
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ControllersAssemblyImpl)
    }

    @Test
    fun `init`() {
        // given
        val miscAssemblyNew = mockk<MiscAssembly>()
        val servicesAssemblyNew = mockk<ServicesAssembly>()

        ControllersAssemblyImpl.init(miscAssemblyNew, servicesAssemblyNew)

        // when
        val resultMiscAssembly = ControllersAssemblyImpl.miscAssembly
        val resultServicesAssembly = ControllersAssemblyImpl.servicesAssembly

        // then
        assertThat(resultMiscAssembly).isEqualTo(miscAssemblyNew)
        assertThat(resultServicesAssembly).isEqualTo(servicesAssemblyNew)
    }

    @Nested
    inner class UserPropertiesControllerTest {
        private val mockPendingPropertiesStorage = mockk<UserPropertiesStorage>()
        private val mockSentPropertiesStorage = mockk<UserPropertiesStorage>()
        private val mockUserPropertiesService = mockk<UserPropertiesService>()
        private val mockWorker = mockk<DelayedWorker>()
        private val slotUserStorageKeys = mutableListOf<String>()

        @BeforeEach
        fun setup() {
            every {
                ControllersAssemblyImpl.providePropertiesStorage(capture(slotUserStorageKeys))
            } answers {
                when (slotUserStorageKeys.last()) {
                    StorageConstants.PendingUserProperties.key -> {
                        mockPendingPropertiesStorage
                    }
                    StorageConstants.SentUserProperties.key -> {
                        mockSentPropertiesStorage
                    }
                    else -> {
                        throw IllegalStateException("Unexpected Storage type")
                    }
                }
            }

            every {
                mockServicesAssembly.userPropertiesService
            } returns mockUserPropertiesService

            every {
                ControllersAssemblyImpl.provideDelayedWorker()
            } returns mockWorker
        }

        @Test
        fun `get user properties controller`() {
            // given
            val expectedResult = UserPropertiesControllerImpl(
                mockPendingPropertiesStorage,
                mockSentPropertiesStorage,
                mockUserPropertiesService,
                mockWorker,
                logger = mockLogger
            )

            // when
            val result = ControllersAssemblyImpl.userPropertiesController

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user properties controllers`() {
            // given

            // when
            val firstResult = ControllersAssemblyImpl.userPropertiesController
            val secondResult = ControllersAssemblyImpl.userPropertiesController

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class PropertiesStorageTest {
        private val mockLocaleStorage = mockk<LocalStorage>()
        private val mockMapDataMapper = mockk<MapDataMapper>()

        @BeforeEach
        fun setup() {
            every {
                mockMiscAssembly.localStorage
            } returns mockLocaleStorage

            every {
                ControllersAssemblyImpl.provideMapDataMapper()
            } returns mockMapDataMapper
        }

        @Test
        fun `get user properties storage`() {
            // given
            val storageKeys = listOf(
                StorageConstants.SentUserProperties.key,
                StorageConstants.PendingUserProperties.key
            )
            storageKeys.forEach { key ->
                val expectedResult = UserPropertiesStorageImpl(
                    mockLocaleStorage,
                    mockMapDataMapper,
                    key,
                    mockLogger
                )

                // when
                val result = ControllersAssemblyImpl.providePropertiesStorage(key)

                // then
                assertThat(result).isEqualToComparingOnlyGivenFields(expectedResult)
            }
        }

        @Test
        fun `get different user properties storages`() {
            // given
            val mockStorageKey = "mockStorageKey"

            // when
            val firstResult = ControllersAssemblyImpl.providePropertiesStorage(mockStorageKey)
            val secondResult = ControllersAssemblyImpl.providePropertiesStorage(mockStorageKey)

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class DelayedWorkerTest {
        @Test
        fun `get delayed worker`() {
            // given

            // when
            val result = ControllersAssemblyImpl.provideDelayedWorker()

            // then
            assertThat(result).isInstanceOf(DelayedWorkerImpl::class.java)
        }

        @Test
        fun `get different delayed workers`() {
            // given

            // when
            val firstResult = ControllersAssemblyImpl.provideDelayedWorker()
            val secondResult = ControllersAssemblyImpl.provideDelayedWorker()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get different map data mappers`() {
        // given

        // when
        val firstResult = ControllersAssemblyImpl.provideMapDataMapper()
        val secondResult = ControllersAssemblyImpl.provideMapDataMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Nested
    inner class GoogleBillingControllerTest {
        private val mockGoogleBillingConsumer = mockk<GoogleBillingConsumer>()
        private val mockGoogleBillingPurchaser = mockk<GoogleBillingPurchaser>()
        private val mockGoogleBillingDataFetcher = mockk<GoogleBillingDataFetcher>()
        private val mockPurchasesListener = mockk<PurchasesListener>()

        @BeforeEach
        fun setup() {
            every {
                ControllersAssemblyImpl.provideGoogleBillingConsumer()
            } returns mockGoogleBillingConsumer

            every {
                ControllersAssemblyImpl.provideGoogleBillingPurchaser()
            } returns mockGoogleBillingPurchaser

            every {
                ControllersAssemblyImpl.provideGoogleBillingDataFetcher()
            } returns mockGoogleBillingDataFetcher
        }

        @Test
        fun `get google billing controller`() {
            // given
            val expectedResult = GoogleBillingControllerImpl(
                mockGoogleBillingConsumer,
                mockGoogleBillingPurchaser,
                mockGoogleBillingDataFetcher,
                mockPurchasesListener,
                mockLogger
            )

            // when
            val result =
                ControllersAssemblyImpl.provideGoogleBillingController(mockPurchasesListener)

            // then
            assertThat(result).isEqualToComparingOnlyGivenFields(expectedResult)
        }

        @Test
        fun `get different google billing controllers`() {
            // given

            // when
            val firstResult =
                ControllersAssemblyImpl.provideGoogleBillingController(mockPurchasesListener)
            val secondResult =
                ControllersAssemblyImpl.provideGoogleBillingController(mockPurchasesListener)

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingConsumerTest {
        @Test
        fun `get google billing consumer`() {
            // given
            val expectedResult = GoogleBillingConsumerImpl(mockLogger)

            // when
            val result = ControllersAssemblyImpl.provideGoogleBillingConsumer()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing consumers`() {
            // given

            // when
            val firstResult = ControllersAssemblyImpl.provideGoogleBillingConsumer()
            val secondResult = ControllersAssemblyImpl.provideGoogleBillingConsumer()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingPurchaserTest {
        @Test
        fun `get google billing purchaser`() {
            // given
            val expectedResult = GoogleBillingPurchaserImpl(mockLogger)

            // when
            val result = ControllersAssemblyImpl.provideGoogleBillingPurchaser()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing purchasers`() {
            // given

            // when
            val firstResult = ControllersAssemblyImpl.provideGoogleBillingPurchaser()
            val secondResult = ControllersAssemblyImpl.provideGoogleBillingPurchaser()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingDataFetcherTest {
        @Test
        fun `get google billing data fetcher`() {
            // given
            val expectedResult = GoogleBillingDataFetcherImpl(mockLogger)

            // when
            val result = ControllersAssemblyImpl.provideGoogleBillingDataFetcher()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google data fetchers`() {
            // given

            // when
            val firstResult = ControllersAssemblyImpl.provideGoogleBillingDataFetcher()
            val secondResult = ControllersAssemblyImpl.provideGoogleBillingDataFetcher()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }
}
