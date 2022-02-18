package com.qonversion.android.sdk.internal.di.controllers

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.billing.PurchasesListener
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.controller.GoogleBillingControllerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.di.cacher.CacherAssemblyImpl
import com.qonversion.android.sdk.internal.di.misc.MiscAssembly
import com.qonversion.android.sdk.internal.di.services.ServicesAssembly
import com.qonversion.android.sdk.internal.di.storage.StorageAssembly
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.controller.UserControllerImpl
import com.qonversion.android.sdk.internal.user.generator.UserIdGenerator
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
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
    private val mockCacherAssembly = mockk<CacherAssemblyImpl>()
    private val mockLogger = mockk<Logger>()
    private val mockInternalConfig = mockk<InternalConfig>()

    @BeforeEach
    fun setup() {
        controllersAssembly =
            ControllersAssemblyImpl(
                mockStorageAssembly,
                mockMiscAssembly,
                mockServicesAssembly,
                mockCacherAssembly,
                mockInternalConfig
            )

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
            assertThat(result).isInstanceOf(UserPropertiesControllerImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user properties controllers`() {
            // given

            // when
            val firstResult = controllersAssembly.userPropertiesController()
            val secondResult = controllersAssembly.userPropertiesController()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
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
                controllersAssembly.googleBillingConsumer()
            } returns mockGoogleBillingConsumer

            every {
                controllersAssembly.googleBillingPurchaser()
            } returns mockGoogleBillingPurchaser

            every {
                controllersAssembly.googleBillingDataFetcher()
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
            assertThat(result).isInstanceOf(GoogleBillingControllerImpl::class.java)
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult,
                "consumer",
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
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingDataFetcherTest {
        @Test
        fun `get google billing data fetcher`() {
            // given
            val expectedResult = GoogleBillingDataFetcherImpl(mockLogger)

            // when
            val result = controllersAssembly.googleBillingDataFetcher()

            // then
            assertThat(result).isInstanceOf(GoogleBillingDataFetcher::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google data fetchers`() {
            // given

            // when
            val firstResult = controllersAssembly.googleBillingDataFetcher()
            val secondResult = controllersAssembly.googleBillingDataFetcher()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingConsumerTest {
        @Test
        fun `get google billing consumer`() {
            // given
            val expectedResult = GoogleBillingConsumerImpl(mockLogger)

            // when
            val result = controllersAssembly.googleBillingConsumer()

            // then
            assertThat(result).isInstanceOf(GoogleBillingConsumerImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing consumers`() {
            // given

            // when
            val firstResult = controllersAssembly.googleBillingConsumer()
            val secondResult = controllersAssembly.googleBillingConsumer()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class GoogleBillingPurchaserTest {
        @Test
        fun `get google billing purchaser`() {
            // given
            val expectedResult = GoogleBillingPurchaserImpl(mockLogger)

            // when
            val result = controllersAssembly.googleBillingPurchaser()

            // then
            assertThat(result).isInstanceOf(GoogleBillingPurchaserImpl::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different google billing purchasers`() {
            // given

            // when
            val firstResult = controllersAssembly.googleBillingPurchaser()
            val secondResult = controllersAssembly.googleBillingPurchaser()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserControllerTest {
        private val mockUserCacher = mockk<Cacher<User?>>()
        private val mockUserService = mockk<UserService>()
        private val mockUserDataStorage = mockk<UserDataStorage>(relaxed = true)
        private val mockUserIdGenerator = mockk<UserIdGenerator>(relaxed = true)
        private val mockAppLifecycleObserver = mockk<AppLifecycleObserver>(relaxed = true)

        @BeforeEach
        fun setup() {
            every { mockCacherAssembly.userCacher() } returns mockUserCacher
            every { mockServicesAssembly.userServiceDecorator() } returns mockUserService
            every { mockStorageAssembly.userDataStorage() } returns mockUserDataStorage
            every { mockMiscAssembly.userIdGenerator() } returns mockUserIdGenerator
            every { mockMiscAssembly.appLifecycleObserver() } returns mockAppLifecycleObserver
        }

        @Test
        fun `get user controller`() {
            // given
            val expectedResult = UserControllerImpl(
                mockUserService,
                mockUserCacher,
                mockUserDataStorage,
                mockInternalConfig,
                mockUserIdGenerator,
                mockAppLifecycleObserver,
                mockLogger
            )

            // when
            val result = controllersAssembly.userController()

            // then
            assertThat(result).isInstanceOf(UserControllerImpl::class.java)
            assertThat(result).isEqualToComparingOnlyGivenFields(
                expectedResult,
                "userService",
                "userCacher",
                "userDataStorage",
                "entitlementsUpdateListenerProvider",
                "logger"
            )
        }

        @Test
        fun `get different user controllers`() {
            // given

            // when
            val firstResult = controllersAssembly.userController()
            val secondResult = controllersAssembly.userController()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}
