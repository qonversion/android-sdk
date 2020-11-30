package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QUserPropertiesManagerTest {
    private var mockContentResolver = mockk<Application>(relaxed = true).contentResolver
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private var mockHandler: Handler = mockk(relaxed = true)

    private lateinit var propertiesManager: QUserPropertiesManager

    @Before
    fun setUp() {
        val slot = slot<Runnable>()
        every {
            mockHandler.post(capture(slot))
        } answers {
            slot.captured.run()
            true
        }

        propertiesManager =
            QUserPropertiesManager(mockRepository, mockContentResolver, mockHandler)
    }

    @Test
    fun forceSendProperties() {
        propertiesManager.forceSendProperties()

        verify(exactly = 1) {
            mockRepository.sendProperties()
        }
    }

    @Test
    fun setUserID() {
        val userId = "userId"

        propertiesManager.setUserID(userId)

        verify(exactly = 1) {
            mockRepository.setProperty(QUserProperties.CustomUserId.userPropertyCode, userId)
        }
    }

    @Test
    fun setProperty() {
        val key = QUserProperties.Email
        val email = "me@qonvesrion.io"

        propertiesManager.setProperty(key, email)

        verify(exactly = 1) {
            mockRepository.setProperty(QUserProperties.Email.userPropertyCode, email)
        }
    }

    @Test
    fun setUserProperty() {
        val key = "key"
        val value = "value"

        propertiesManager.setUserProperty(key, value)

        verify(exactly = 1) {
            mockRepository.setProperty(key, value)
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

        propertiesManager =
            QUserPropertiesManager(mockRepository, mockContentResolver, mockHandler)

        verify {
            mockRepository.setProperty(
                QUserProperties.FacebookAttribution.userPropertyCode,
                fbAttributionId
            )
        }
    }

    @Test
    fun `init when fbAttributionId is null`() {
        mockkConstructor(FacebookAttribution::class)
        every { anyConstructed<FacebookAttribution>().getAttributionId(mockContentResolver) } returns null

        propertiesManager =
            QUserPropertiesManager(mockRepository, mockContentResolver, mockHandler)

        verify(exactly = 0) {
            mockRepository.setProperty(
                any(),
                any()
            )
        }
    }
}