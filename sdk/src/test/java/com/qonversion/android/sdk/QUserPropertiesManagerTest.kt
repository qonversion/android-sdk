package com.qonversion.android.sdk

import android.app.Application
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.dto.automations.ActionPointScreen
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@RunWith(AndroidJUnit4::class)
class QUserPropertiesManagerTest {
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockContentResolver = mockk<ContentResolver>(relaxed = true)
    private val mockPropertiesStorage = mockk<PropertiesStorage>(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)
    private val fieldIsRequestInProgress = "isRequestInProgress"
    private val properties = mapOf("someKey" to "someValue")

    private lateinit var mockHandler: Handler
    private lateinit var propertiesManager: QUserPropertiesManager

    @Before
    fun setUp() {
        every {
            mockContext.contentResolver
        } returns mockContentResolver

        val mockLooper = mockk<Looper>()
        every {
            mockContext.mainLooper
        } returns mockLooper

        mockkConstructor(Handler::class)
        mockHandler = Handler(mockLooper)

        val slot = slot<Runnable>()
        every {
            mockHandler.postDelayed(capture(slot), 5000)
        } answers {
            slot.captured.run()
            true
        }

        every { mockPropertiesStorage.getProperties() } returns properties

        propertiesManager = QUserPropertiesManager(mockContext, mockRepository, mockPropertiesStorage, mockLogger)
    }

    @Test
    fun forceSendProperties_requestInProgress() {
        propertiesManager.mockPrivateField(fieldIsRequestInProgress, true)

        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(any(), any(), any())
        }
    }

    @Test
    fun forceSendProperties_onError() {
        propertiesManager.mockPrivateField(fieldIsRequestInProgress, false)

        every {
            mockRepository.sendProperties(properties, any(), captureLambda())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.forceSendProperties()

        verify(exactly = 2) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
        }

        val memberProperty = QUserPropertiesManager::class.memberProperties.find { it.name == fieldIsRequestInProgress }
        memberProperty?.let {
            it.isAccessible = true
            val isRequestInProgress = it.get(propertiesManager) as Boolean? // здесь приводишь к нужному типу
            assertThat(isRequestInProgress).isEqualTo(false) // ну здесь логика, какая нужна
        }
    }

    @Test
    fun forceSendProperties_onSuccess() {
        propertiesManager.mockPrivateField(fieldIsRequestInProgress, false)

        every {
            mockRepository.sendProperties(properties, captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.forceSendProperties()

        verify(exactly = 2) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
        }

        verify {
            mockPropertiesStorage.clear(properties)
        }

        val memberProperty = QUserPropertiesManager::class.memberProperties.find { it.name == fieldIsRequestInProgress }
        memberProperty?.let {
            it.isAccessible = true
            val isRequestInProgress = it.get(propertiesManager) as Boolean? // здесь приводишь к нужному типу
            assertThat(isRequestInProgress).isEqualTo(false) // ну здесь логика, какая нужна
        }
    }

    @Test
    fun setUserID() {
        val userId = "userId"

        val spyUserPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        justRun { spyUserPropertiesManager.setUserProperty("_q_custom_user_id", userId) }

        spyUserPropertiesManager.setUserID(userId)

        verify() {
            spyUserPropertiesManager.setUserProperty("_q_custom_user_id", userId)
        }
    }

    @Test
    fun setUserProperty_emptyValue() {
        val key = QUserProperties.Email
        val value = ""

        propertiesManager.setProperty(key, value)

        verify(exactly = 0) {
            mockPropertiesStorage.save(key.userPropertyCode, value)
        }

        verify(exactly = 1) {
            mockHandler.postDelayed(any(), any())
        }
    }

    @Test
    fun setUserProperty_requestInProgress() {
        val key = QUserProperties.Email
        val value = "some value"

        propertiesManager.mockPrivateField(fieldIsRequestInProgress, true)

        propertiesManager.setProperty(key, value)

        verify(exactly = 1) {
            mockPropertiesStorage.save(key.userPropertyCode, value)
            mockHandler.postDelayed(any(), any())
        }
    }

    @Test
    fun setUserProperty() {
        val key = "_q_email"
        val value = "some value"
        val handlerDelay = (5 * 1000).toLong()

        propertiesManager.mockPrivateField(fieldIsRequestInProgress, false)

        val properties = mapOf("someKey" to "someValue")

        every {
            mockRepository.sendProperties(properties, captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 1) {
            mockPropertiesStorage.save(key, value)
            mockPropertiesStorage.clear(properties)
        }

        verify(exactly = 2) {
            mockHandler.postDelayed(any(), handlerDelay)
            mockRepository.sendProperties(properties, any(), any())
        }

    }

    @Test
    fun setProperty() {
        val key = QUserProperties.Email
        val value = "me@qonversion.io"

        val spyUserPropertiesManager = spyk(propertiesManager, recordPrivateCalls = true)
        justRun { spyUserPropertiesManager.setUserProperty("_q_email", value) }

        spyUserPropertiesManager.setProperty(key, value)

        verify() {
            spyUserPropertiesManager.setUserProperty("_q_email", value)
        }
    }

    @Test
    fun sendPropertiesAtPeriod() {
        val delayMillis: Long = 5 * 1000

        verify(exactly = 1) {
            mockHandler.postDelayed(any(), delayMillis)
        }
    }

    @Test
    fun `init when fbAttributionId is not null`() {
        val fbAttributionId = "fbAttributionId"
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns fbAttributionId

        propertiesManager = QUserPropertiesManager(mockContext, mockRepository, mockPropertiesStorage, mockLogger)

        verify(exactly = 1) {
            mockPropertiesStorage.save(
                "_q_fb_attribution",
                fbAttributionId
            )
        }
    }

    @Test
    fun `init when fbAttributionId is null`() {
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns null

        propertiesManager = QUserPropertiesManager(mockContext, mockRepository, mockPropertiesStorage, mockLogger)

        verify(exactly = 0) {
            mockPropertiesStorage.save(
                any(),
                any()
            )
        }
    }
}