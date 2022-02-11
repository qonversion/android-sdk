package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesService
import com.qonversion.android.sdk.internal.userProperties.UserPropertiesStorage
import com.qonversion.android.sdk.internal.userProperties.controller.UserPropertiesControllerImpl
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested

import org.junit.jupiter.api.Test

internal class ControllersAssemblyTest {
    private lateinit var controllersAssembly: ControllersAssemblyImpl

    private val mockMiscAssembly = mockk<MiscAssembly>()
    private val mockServicesAssembly = mockk<ServicesAssembly>()
    private val mockStorageAssembly = mockk<StorageAssembly>()
    private val mockLogger = mockk<Logger>()

    @BeforeEach
    fun setup() {
        controllersAssembly =
            ControllersAssemblyImpl(mockStorageAssembly, mockMiscAssembly, mockServicesAssembly)

        every {
            mockMiscAssembly.logger()
        } returns mockLogger
    }

    @Nested
    inner class UserPropertiesControllerTest {
        private val mockPendingPropertiesStorage = mockk<UserPropertiesStorage>()
        private val mockSentPropertiesStorage = mockk<UserPropertiesStorage>()
        private val mockUserPropertiesService = mockk<UserPropertiesService>()
        private val mockWorker = mockk<DelayedWorker>()

        @BeforeEach
        fun setup() {
            every {
                mockStorageAssembly.sentUserPropertiesStorage()
            } returns mockSentPropertiesStorage
            every {
                mockStorageAssembly.pendingUserPropertiesStorage()
            } returns mockPendingPropertiesStorage

            every {
                mockServicesAssembly.userPropertiesService()
            } returns mockUserPropertiesService

            every {
                mockMiscAssembly.delayedWorker()
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
            val result = controllersAssembly.userPropertiesController()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user properties controllers`() {
            // given

            // when
            val firstResult = controllersAssembly.userPropertiesController()
            val secondResult = controllersAssembly.userPropertiesController()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingControllerTest {
        private val mockGoogleBillingConsumer = mockk<GoogleBillingConsumer>()
        private val mockGoogleBillingPurchaser = mockk<GoogleBillingPurchaser>()
        private val mockGoogleBillingDataFetcher = mockk<GoogleBillingDataFetcher>()
        private val mockPurchasesListener = mockk<PurchasesListener>()

        @BeforeEach
        fun setup() {
            controllersAssembly = spyk(controllersAssembly)

            every {
                controllersAssembly.provideGoogleBillingConsumer()
            } returns mockGoogleBillingConsumer

            every {
                controllersAssembly.provideGoogleBillingPurchaser()
            } returns mockGoogleBillingPurchaser

            every {
                controllersAssembly.provideGoogleBillingDataFetcher()
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
                controllersAssembly.googleBillingController(mockPurchasesListener)

            // then
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult, "consumer",
                "purchaser",
                "dataFetcher",
                "purchasesListener",
                "logger"
            )
        }

        @Test
        fun `get different google billing controllers`() {
            // given

            // when
            val firstResult =
                controllersAssembly.googleBillingController(mockPurchasesListener)
            val secondResult =
                controllersAssembly.googleBillingController(mockPurchasesListener)

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
            val result = controllersAssembly.provideGoogleBillingDataFetcher()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google data fetchers`() {
            // given

            // when
            val firstResult = controllersAssembly.provideGoogleBillingDataFetcher()
            val secondResult = controllersAssembly.provideGoogleBillingDataFetcher()

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
            val result = controllersAssembly.provideGoogleBillingConsumer()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing consumers`() {
            // given

            // when
            val firstResult = controllersAssembly.provideGoogleBillingConsumer()
            val secondResult = controllersAssembly.provideGoogleBillingConsumer()

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
            val result = controllersAssembly.provideGoogleBillingPurchaser()

            // then
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing purchasers`() {
            // given

            // when
            val firstResult = controllersAssembly.provideGoogleBillingPurchaser()
            val secondResult = controllersAssembly.provideGoogleBillingPurchaser()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }
}
