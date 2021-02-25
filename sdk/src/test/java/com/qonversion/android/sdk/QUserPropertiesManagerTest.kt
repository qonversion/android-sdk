package com.qonversion.android.sdk

import android.app.Application
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qonversion.android.sdk.storage.CustomUidStorage
import com.qonversion.android.sdk.storage.PropertiesStorage
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QUserPropertiesManagerTest {
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockContentResolver = mockk<ContentResolver>(relaxed = true)
    private val mockPropertiesStorage = mockk<PropertiesStorage>(relaxed = true)
    private val mockCustomUidStorage = mockk<CustomUidStorage>(relaxed = true)

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
            mockHandler.post(capture(slot))
        } answers {
            slot.captured.run()
            true
        }

        propertiesManager = createUserPropertiesManager()
    }

    @Test
    fun `should force send properties when properties storage is not empty`() {
        every { mockPropertiesStorage.getProperties() } returns mapOf("key" to "value")
        every {
            mockRepository.sendProperties(mockPropertiesStorage.getProperties(), captureLambda())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockRepository.sendProperties(mockPropertiesStorage.getProperties(), any())
            mockPropertiesStorage.clear()
        }
    }

    @Test
    fun `should not force send properties when properties storage is empty`() {
        every { mockPropertiesStorage.getProperties() } returns mapOf()

        propertiesManager.forceSendProperties()
        verify(exactly = 1) {
            mockPropertiesStorage.getProperties()
        }

        verify(exactly = 0) {
            mockRepository.sendProperties(any(), any())
            mockPropertiesStorage.clear()
        }
    }

    @Test
    fun setUserID() {
        val userId = "userId"

        propertiesManager.setUserID(userId)

        verify(exactly = 1) {
            mockPropertiesStorage.save(QUserProperties.CustomUserId.code, userId)
        }
    }

    @Test
    fun setProperty() {
        val key = QUserProperties.Email
        val email = "me@qonvesrion.io"

        propertiesManager.setProperty(key, email)

        verify(exactly = 1) {
            mockPropertiesStorage.save(QUserProperties.Email.code, email)
        }
    }

    @Test
    fun setUserProperty() {
        val key = "key"
        val value = "value"

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 1) {
            mockPropertiesStorage.save(key, value)
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
    fun `should set facebook property when fbAttributionId is not null`() {
        val fbAttributionId = "fbAttributionId"
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns fbAttributionId

        createUserPropertiesManager()

        verify(exactly = 1) {
            mockPropertiesStorage.save(
                QUserProperties.FacebookAttribution.code,
                fbAttributionId
            )
        }
    }

    @Test
    fun `should not set facebook property when fbAttributionId is null`() {
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns null

        createUserPropertiesManager()

        verify(exactly = 0) {
            mockPropertiesStorage.save(
                any(),
                any()
            )
        }
    }

    private fun createUserPropertiesManager() =
        QUserPropertiesManager(
            mockContext,
            mockRepository,
            mockPropertiesStorage,
            mockCustomUidStorage
        )

}