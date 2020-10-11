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
    private lateinit var propertiesManagerMock: QUserPropertiesManager
    private var contentResolver = mockk<Application>(relaxed = true).contentResolver
    private val repositoryMock = mockk<QonversionRepository>(relaxed = true)
    private var handler: Handler = mockk(relaxed = true)

    @Before
    fun setUp() {
        val slot = slot<Runnable>()
        every {
            handler.post(capture(slot))
        } answers {
            slot.captured.run()
            true
        }

        propertiesManagerMock =
            QUserPropertiesManager(repositoryMock, contentResolver, handler)
    }

    @Test
    fun forceSendProperties() {
        propertiesManagerMock.forceSendProperties()

        verify(exactly = 1) {
            repositoryMock.sendProperties()
        }
    }

    @Test
    fun setUserID() {
        val userId = "userId"

        propertiesManagerMock.setUserID(userId)

        verify(exactly = 1) {
            repositoryMock.setProperty(QUserProperties.CustomUserId.userPropertyCode, userId)
        }
    }

    @Test
    fun setProperty() {
        val key = QUserProperties.Email
        val email = "me@qonvesrion.io"

        propertiesManagerMock.setProperty(key, email)

        verify(exactly = 1) {
            repositoryMock.setProperty(QUserProperties.Email.userPropertyCode, email)
        }
    }

    @Test
    fun setUserProperty() {
        val key = "key"
        val value = "value"

        propertiesManagerMock.setUserProperty(key, value)

        verify(exactly = 1) {
            repositoryMock.setProperty(key, value)
        }
    }

    @Test
    fun sendPropertiesAtPeriod() {
        val delayMillis: Long = 5 * 1000

        verify(exactly = 1) {
            handler.postDelayed(any(), delayMillis)
        }
    }
}