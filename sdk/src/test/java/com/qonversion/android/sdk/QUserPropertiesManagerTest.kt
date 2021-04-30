package com.qonversion.android.sdk

import android.app.Application
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PropertiesStorage
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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

        mockkConstructor(Handler::class)
        mockHandler = Handler(mockLooper)

        val slot = slot<Runnable>()
        every {
            mockHandler.postDelayed(capture(slot), 5000)
        } answers {
            slot.captured.run()
            true
        }

        propertiesManager =
            QUserPropertiesManager(mockContext, mockRepository, mockPropertiesStorage, mockLogger)
    }

    @Test
    fun `should send facebook attribution when it is not null`() {
        val fbAttributionId = "fbAttributionId"
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns fbAttributionId

        propertiesManager.sendFacebookAttribution()

        verify(exactly = 1) {
            propertiesManager.setUserProperty(
                "_q_fb_attribution",
                fbAttributionId
            )
        }
    }

    @Test
    fun `should not send facebook attribution when it is null`() {
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns null

        propertiesManager.sendFacebookAttribution()

        verify(exactly = 0) {
            propertiesManager.setUserProperty(
                any(),
                any()
            )
        }
    }

    @Test
    fun `should not force send properties when request is in progress`() {
        propertiesManager.mockPrivateField(fieldIsRequestInProgress, true)

        propertiesManager.forceSendProperties()

        verify(exactly = 0) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(any(), any(), any())
        }

        assertThatIsRequestInProgressIsEqualTo(true)
    }

    @Test
    fun `should not force send properties when properties storage is empty`() {
        every { mockPropertiesStorage.getProperties() } returns mapOf()

        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
        }

        verify(exactly = 0) {
            mockRepository.sendProperties(any(), any(), any())
            mockPropertiesStorage.clear(any())
        }

        assertThatIsRequestInProgressIsEqualTo(false)
    }

    @Test
    fun `should force send properties and get response in onError callback`() {
        every { mockPropertiesStorage.getProperties() } returns properties

        every {
            mockRepository.sendProperties(properties, any(), captureLambda())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
        }
        verify(exactly = 0) {
            mockPropertiesStorage.clear(any())
        }

        assertThatIsRequestInProgressIsEqualTo(false)
    }

    @Test
    fun `should force send properties and get response in onSuccess callback`() {
        every { mockPropertiesStorage.getProperties() } returns properties

        every {
            mockRepository.sendProperties(properties, captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
            mockRepository.sendProperties(properties, any(), any())
            mockPropertiesStorage.clear(properties)
        }

        assertThatIsRequestInProgressIsEqualTo(false)
    }

    @Test
    fun setProperty() {
        val key = QUserProperties.Email
        val value = "me@qonversion.io"

        propertiesManager.setProperty(key, value)

        verify {
            propertiesManager.setUserProperty("_q_email", value)
        }
    }

    @Test
    fun setUserID() {
        val userId = "userId"

        propertiesManager.setUserID(userId)

        verify(exactly = 1) {
            propertiesManager.setUserProperty("_q_custom_user_id", userId)
        }
    }

    @Test
    fun `should not set user property when its value is empty`() {
        val key = "email"
        val value = ""

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 0) {
            mockPropertiesStorage.save(any(), any())
            mockHandler.postDelayed(any(), any())
        }
    }

    @Test
    fun `should set and send user property when its is not empty and request is not in progress`() {
        val key = "_q_email"
        val value = "some value"
        val handlerDelay = (5 * 1000).toLong()

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 1) {
            mockPropertiesStorage.save(key, value)
            mockHandler.postDelayed(any(), handlerDelay)
            propertiesManager.forceSendProperties()
        }
    }

    @Test
    fun `should not send user property when request is in progress`() {
        val key = "email"
        val value = "value"

        propertiesManager.mockPrivateField(fieldIsRequestInProgress, true)

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 0) {
            mockHandler.postDelayed(any(), any())
            propertiesManager.forceSendProperties()
        }
    }

    private fun assertThatIsRequestInProgressIsEqualTo(value: Boolean) {
        val memberProperty =
            QUserPropertiesManager::class.memberProperties.find { it.name == fieldIsRequestInProgress }

        memberProperty?.let {
            it.isAccessible = true
            val isRequestInProgress =
                it.get(propertiesManager) as Boolean?
            assertThat(isRequestInProgress).isEqualTo(value)
        }
    }
}