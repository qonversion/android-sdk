package com.qonversion.android.sdk.internal

import android.app.Application
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.internal.dto.SendPropertiesResult
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.mockPrivateField
import com.qonversion.android.sdk.internal.storage.PropertiesStorage
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertAll
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class QUserPropertiesManagerTest {
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QRepository>(relaxed = true)
    private val mockContentResolver = mockk<ContentResolver>(relaxed = true)
    private val mockPropertiesStorage = mockk<PropertiesStorage>(relaxed = true)
    private val mockIncrementalCalculator = mockk<IncrementalDelayCalculator>(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockAppStateProvider = mockk<AppStateProvider>(relaxed = true)

    private val fieldIsRequestInProgress = "isRequestInProgress"
    private val fieldRetryDelay = "retryDelay"
    private val fieldRetriesCounter = "retriesCounter"
    private val fieldIsSendingScheduled = "isSendingScheduled"
    private val fieldHandler = "handler"
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

        propertiesManager = spyk(
            QUserPropertiesManager(
                mockContext,
                mockRepository,
                mockPropertiesStorage,
                mockIncrementalCalculator,
                mockAppStateProvider,
                mockLogger
            )
        )
        propertiesManager.mockPrivateField(fieldHandler, mockHandler)
    }

    @Test
    fun `should send facebook attribution when it is not null`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        val fbAttributionId = "fbAttributionId"

        // when
        spykPropertiesManager.onFbAttributionIdResult(fbAttributionId)

        // then
        verify(exactly = 1) {
            spykPropertiesManager.setCustomUserProperty(
                "_q_fb_attribution",
                fbAttributionId
            )
        }
    }

    @Test
    fun `should not send facebook attribution when it is null`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        // when
        spykPropertiesManager.onFbAttributionIdResult(null)

        // then
        verify(exactly = 0) {
            spykPropertiesManager.setCustomUserProperty(
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
            { assertEquals("The field isRequestInProgress is not equal true", true, isRequestInProgress) },
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
        }

        verify(exactly = 0) {
            mockPropertiesStorage.clear(any())
        }

        val isRequestInProgress =
            propertiesManager.getPrivateField<Boolean>(fieldIsRequestInProgress)
        val retryDelay = propertiesManager.getPrivateField<Int>(fieldRetryDelay)
        val retriesCounter = propertiesManager.getPrivateField<Int>(fieldRetriesCounter)

        assertAll(
            "Private members haven't been changed to calculate new delay",
            { assertEquals("The field isRequestInProgress is not equal false", false, isRequestInProgress) },
            { assertEquals("The field retryDelay is not equal calculatedDelay", retryDelay, calculatedDelay) },
            { assertEquals("The field retriesCounter is not equal 1", 1, retriesCounter) }
        )
    }

    @Test
    fun `should force send properties again after failed attempt on foreground`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        mockPropertiesStorage(properties)
        mockErrorSendPropertiesResponse(properties)
        every { mockAppStateProvider.appState } returns AppState.Foreground
        every { spykPropertiesManager.retryPropertiesRequest() } just runs

        // when
        spykPropertiesManager.forceSendProperties()

        // then
        verify(exactly = 1) {
            spykPropertiesManager.retryPropertiesRequest()
        }
    }

    @Test
    fun `should not force send properties again after failed attempt on background`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        mockPropertiesStorage(properties)
        mockErrorSendPropertiesResponse(properties)
        mockIncrementalCounterResponse(calculatedDelay)
        mockPostDelayed((calculatedDelay * 1000).toLong())
        every { mockAppStateProvider.appState } returns AppState.Background

        // when
        spykPropertiesManager.forceSendProperties()

        // then
        verify(exactly = 1) {
            spykPropertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `should force send properties and get response in onSuccess callback`() {
        // given
        mockPropertiesStorage(properties)

        val propertiesResult = SendPropertiesResult(
            emptyList(),
            emptyList()
        )

        every {
            mockRepository.sendProperties(properties, captureLambda(), any())
        } answers {
            lambda<(SendPropertiesResult) -> Unit>().captured.invoke(propertiesResult)
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
        val key = QUserPropertyKey.Email
        val value = "me@qonversion.io"
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        // when
        spykPropertiesManager.setUserProperty(key, value)

        // then
        verify {
            spykPropertiesManager.setCustomUserProperty("_q_email", value)
        }
    }

    @Test
    fun `should not set user property when its value is empty`() {
        // given
        val key = "email"
        val value = ""

        // when
        propertiesManager.setCustomUserProperty(key, value)

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
        val key = "email"
        val value = "some value"

        propertiesManager.mockPrivateField(fieldIsSendingScheduled, true)
        every { propertiesManager.sendPropertiesWithDelay(any()) } just runs

        // when
        propertiesManager.setCustomUserProperty(key, value)

        // then
        verify {
            mockPropertiesStorage.save(key, value)
        }
        verify(exactly = 0) {
            propertiesManager.sendPropertiesWithDelay(any())
        }
    }

    @Test
    fun `should set and send user property when it is not empty and sending is not scheduled`() {
        // given
        val key = "_q_email"
        val value = "some value"
        every { mockAppStateProvider.appState } returns AppState.Foreground
        every { propertiesManager.sendPropertiesWithDelay(any()) } just runs

        // when
        propertiesManager.setCustomUserProperty(key, value)

        // then
        verifyOrder {
            mockPropertiesStorage.save(key, value)
            propertiesManager.sendPropertiesWithDelay(minDelay)
        }
    }

    @Test
    fun `on app foreground when properties is not empty`() {
        // given
        propertiesManager = spyk(propertiesManager)
        every { propertiesManager.sendPropertiesWithDelay(minDelay) } just runs

        mockPropertiesStorage(mapOf("testKey" to "testValue"))

        // when
        propertiesManager.onAppForeground()

        // then
        verify(exactly = 1) {
            propertiesManager.sendPropertiesWithDelay(minDelay)
        }
    }

    @Test
    fun `on app foreground when properties is empty`() {
        // given
        propertiesManager = spyk(propertiesManager)
        mockPropertiesStorage(emptyMap())

        // when
        propertiesManager.onAppForeground()

        verify(exactly = 0) {
            propertiesManager.sendPropertiesWithDelay(any())
        }
    }

    @Test
    fun `send properties with delay on background`() {
        // given
        every { mockAppStateProvider.appState } returns AppState.Background
        val mockDelay = 10

        // when
        propertiesManager.sendPropertiesWithDelay(mockDelay)

        // then
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        assertEquals(false, isSendingScheduled)

        verify(exactly = 0) {
            mockHandler.postDelayed(any(), any())
            propertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `send properties with delay on foreground`() {
        // given
        every { mockAppStateProvider.appState } returns AppState.Foreground

        val handlerDelay = (minDelay * 1000).toLong()
        mockPostDelayed(handlerDelay)

        every { propertiesManager.forceSendProperties() } just Runs

        // when
        propertiesManager.sendPropertiesWithDelay(minDelay)

        // then
        val isSendingScheduled = propertiesManager.getPrivateField<Boolean>(fieldIsSendingScheduled)
        assertEquals(true, isSendingScheduled)

        verifyOrder {
            mockHandler.postDelayed(any(), handlerDelay)
            propertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `should force send properties when onAppBackground is called`() {
        // given
        val spykPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)

        every {
            spykPropertiesManager.forceSendProperties()
        } just Runs

        // when
        spykPropertiesManager.onAppBackground()

        // then
        verify (exactly = 1) {
            spykPropertiesManager.forceSendProperties()
        }
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
            lambda<(QonversionError) -> Unit>().captured.invoke(QonversionError(QonversionErrorCode.BackendError))
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