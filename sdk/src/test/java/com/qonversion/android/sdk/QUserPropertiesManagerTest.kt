package com.qonversion.android.sdk

import android.app.Application
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertAll
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QUserPropertiesManagerTest {
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockContentResolver = mockk<ContentResolver>(relaxed = true)
    private val mockPropertiesStorage = mockk<PropertiesStorage>(relaxed = true)
    private val mockIncrementalCalculator = mockk<IncrementalCalculator>(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)

    private val fieldIsRequestInProgress = "isRequestInProgress"
    private val fieldRetryDelay = "retryDelay"
    private val fieldRetriesCounter = "retriesCounter"
    private val fieldIsSendingScheduled = "isSendingScheduled"
    private val minDelay = 5
    private val calculatedDelay = 1
    private val properties = mapOf("someKey" to "someValue")

    private lateinit var mockHandler: Handler
    private lateinit var propertiesManager: QUserPropertiesManager

    @Before
    fun setUp() {
        every {
            mockContext.contentResolver
        } returns mockContentResolver

        val mockLooper = mockk<Looper>()

        mockkConstructor(Handler::class)
        mockHandler = Handler(mockLooper)

        propertiesManager =
            QUserPropertiesManager(
                mockContext,
                mockRepository,
                mockPropertiesStorage,
                mockIncrementalCalculator,
                mockLogger
            )
    }

    @Test
    fun `should send facebook attribution when it is not null`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        val fbAttributionId = "fbAttributionId"
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns fbAttributionId

        // when
        spykPropertiesManager.sendFacebookAttribution()

        // then
        verify(exactly = 1) {
            spykPropertiesManager.setUserProperty(
                "_q_fb_attribution",
                fbAttributionId
            )
        }
    }

    @Test
    fun `should not send facebook attribution when it is null`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns null

        // when
        spykPropertiesManager.sendFacebookAttribution()

        // then
        verify(exactly = 0) {
            spykPropertiesManager.setUserProperty(
                any(),
                any()
            )
        }
    }

    @Test
    fun `should not force send properties when request is in progress`() {
        // given
        propertiesManager.mockPrivateField(fieldIsRequestInProgress, true)

        // when
        propertiesManager.forceSendProperties()

        // then
        verify {
            listOf(
                mockIncrementalCalculator,
                mockPropertiesStorage,
                mockRepository,
                mockHandler
            ) wasNot Called
        }

        val isRequestInProgress =
            propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        val retryDelay = propertiesManager.getPrivateField<Int>(fieldRetryDelay)
        val retriesCounter = propertiesManager.getPrivateField<Int>(fieldRetriesCounter)

        assertAll(
            "Private members have been changed",
            { assertEquals("The field isRequestInProgress is not equal true",true, isRequestInProgress) },
            { assertEquals("The field retryDelay is not equal minDelay", minDelay, retryDelay) },
            { assertEquals("The field retriesCounter is not equal 0", 0, retriesCounter) },
            { assertEquals("The field isSendingScheduled is not equal false", false, isSendingScheduled) }
        )
    }

    @Test
    fun `should not force send properties when properties storage is empty`() {
        // given
        mockPropertiesStorage(mapOf())

        // when
        propertiesManager.forceSendProperties()

        // then
        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
        }
        verify(exactly = 0) {
            mockPropertiesStorage.clear(any())
        }
        verify {
            listOf(
                mockIncrementalCalculator,
                mockRepository,
                mockHandler
            ) wasNot Called
        }

        val isRequestInProgress =
            propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        val retryDelay = propertiesManager.getPrivateField<Int>(fieldRetryDelay)
        val retriesCounter = propertiesManager.getPrivateField<Int>(fieldRetriesCounter)

        assertAll(
            "Private members have been changed",
            { assertEquals("The field isRequestInProgress is not equal false", false, isRequestInProgress) },
            { assertEquals("The field retryDelay is not equal minDelay", minDelay, retryDelay) },
            { assertEquals("The field retriesCounter is not equal 0", 0, retriesCounter) },
            { assertEquals("The field isSendingScheduled is not equal false", false, isSendingScheduled) }
        )
    }

    @Test
    fun `should set isRequestInProgress to true and isSendingScheduled to false when properties storage is not empty `() {
        // given
        mockPropertiesStorage(properties)

        // when
        propertiesManager.forceSendProperties()

        val isRequestInProgress = propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)

        assertAll(
            "Private members haven't been changed",
            { assertEquals("The field isRequestInProgress is not equal true", true, isRequestInProgress) },
            { assertEquals("The field isSendingScheduled is not equal false", false, isSendingScheduled) }
        )
    }

    @Test
    fun `should force send properties and get response in onError callback`() {
        // given
        val handlerDelay = (calculatedDelay * 1000).toLong()
        mockPropertiesStorage(properties)
        mockErrorSendPropertiesResponse(properties)
        mockIncrementalCounterResponse(calculatedDelay)

        // when
        propertiesManager.forceSendProperties()

        // then
        verifyOrder {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
            mockIncrementalCalculator.countDelay(minDelay, 1)
            mockHandler.postDelayed(any(), handlerDelay)
        }

        verify(exactly = 0) {
            mockPropertiesStorage.clear(any())
        }

        val isRequestInProgress =
            propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        val retryDelay = propertiesManager.getPrivateField<Int>(fieldRetryDelay)
        val retriesCounter = propertiesManager.getPrivateField<Int>(fieldRetriesCounter)

        assertAll(
            "Private members haven't been changed to calculate new delay",
            { assertEquals("The field isRequestInProgress is not equal false", false, isRequestInProgress) },
            { assertEquals("The field retryDelay is not equal calculatedDelay", retryDelay, calculatedDelay) },
            { assertEquals("The field retriesCounter is not equal 1", 1, retriesCounter) },
            { assertEquals("The field isSendingScheduled is not equal true", true, isSendingScheduled) }
        )
    }

    @Test
    fun `should force send properties recursively after calculated delay`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        mockPropertiesStorage(properties)
        mockErrorSendPropertiesResponse(properties)
        mockIncrementalCounterResponse(calculatedDelay)
        mockPostDelayed((calculatedDelay * 1000).toLong())

        // when
        spykPropertiesManager.forceSendProperties()

        // then
        verify(exactly = 2) {
            spykPropertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `should force send properties and get response in onSuccess callback`() {
        // given
        mockPropertiesStorage(properties)
        every {
            mockRepository.sendProperties(properties, captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        // when
        propertiesManager.forceSendProperties()

        // then
        verifySequence {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
            mockPropertiesStorage.clear(properties)
        }

        val isRequestInProgress =
            propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        val retryDelay = propertiesManager.getPrivateField<Int>(fieldRetryDelay)
        val retriesCounter = propertiesManager.getPrivateField<Int>(fieldRetriesCounter)

        assertAll(
            "Private members haven't been reset",
            { assertEquals("The field isRequestInProgress is not equal false", false, isRequestInProgress) },
            { assertEquals("The field retryDelay is not equal minDelay", minDelay, retryDelay) },
            { assertEquals("The field retriesCounter is not equal 0", 0, retriesCounter) },
            { assertEquals("The field isSendingScheduled is not equal false", false, isSendingScheduled) }
        )
    }

    @Test
    fun setProperty() {
        // given
        val key = QUserProperties.Email
        val value = "me@qonversion.io"
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        // when
        spykPropertiesManager.setProperty(key, value)

        // then
        verify {
            spykPropertiesManager.setUserProperty("_q_email", value)
        }
    }

    @Test
    fun setUserID() {
        // given
        val userId = "userId"
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        // when
        spykPropertiesManager.setUserID(userId)

        // then
        verify(exactly = 1) {
            spykPropertiesManager.setUserProperty("_q_custom_user_id", userId)
        }
    }

    @Test
    fun `should not set user property when its value is empty`() {
        // given
        val key = "email"
        val value = ""

        // when
        propertiesManager.setUserProperty(key, value)

        // then
        verify {
            listOf(
                mockPropertiesStorage,
                mockHandler
            ) wasNot Called
        }
    }

    @Test
    fun `should set and not send user property when sending properties is scheduled`() {
        // given
        val handlerDelay = (minDelay * 1000).toLong()
        val key = "email"
        val value = "some value"

        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        spykPropertiesManager.mockPrivateField(fieldIsSendingScheduled, true)
        mockPostDelayed(handlerDelay)

        // when
        spykPropertiesManager.setUserProperty(key, value)

        // then
        verify(exactly = 0) {
            spykPropertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `should set and send user property when it is not empty and sending is not scheduled`() {
        // given
        val key = "_q_email"
        val value = "some value"
        val handlerDelay = (minDelay * 1000).toLong()
        mockPostDelayed(handlerDelay)

        every{
            propertiesManager.forceSendProperties()
        } just Runs

        // when
        propertiesManager.setUserProperty(key, value)

        // then
        verifyOrder {
            mockPropertiesStorage.save(key, value)
            mockHandler.postDelayed(any(), handlerDelay)
            propertiesManager.forceSendProperties()
        }

        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        assertEquals("The field isSendingScheduled hasn't been changed to true",true, isSendingScheduled)
    }

    private fun mockPropertiesStorage(properties: Map<String, String>) {
        every {
            mockPropertiesStorage.getProperties()
        } returns properties
    }

    private fun mockErrorSendPropertiesResponse(properties: Map<String, String>) {
        every {
            mockRepository.sendProperties(properties, any(), captureLambda())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }
    }

    private fun mockIncrementalCounterResponse(delay: Int) {
        every {
            mockIncrementalCalculator.countDelay(minDelay, 1)
        } returns delay
    }

    private fun mockPostDelayed(delay: Long) {
        val slot = slot<Runnable>()
        every {
            mockHandler.postDelayed(capture(slot), delay)
        } answers {
            slot.captured.run()
            true
        }
    }
}