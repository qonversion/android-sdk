package com.qonversion.android.sdk

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QUserPropertiesManagerTest {
    private lateinit var propertiesManagerMock: QUserPropertiesManager
    private var applicationMock = mockk<Application>(relaxed = true)
    private val repositoryMock = mockk<QonversionRepository>()

    @Before
    fun setUp() {
        propertiesManagerMock = QUserPropertiesManager(applicationMock, repositoryMock)
    }

    @Test
    fun sendPropertiesOnAppBackground() {
        every {
            repositoryMock.sendProperties()
        } just Runs

        propertiesManagerMock.forceSendProperties()

        verify(exactly = 1) {
            repositoryMock.sendProperties()
        }
    }

    @Test
    fun setPropertyUserId() {
        val userId = "userId"
        every {
            repositoryMock.setProperty(QUserProperties.CustomUserId.userPropertyCode, userId)
        } just Runs

        propertiesManagerMock.setUserID(userId)

        verify(exactly = 1) {
            repositoryMock.setProperty(QUserProperties.CustomUserId.userPropertyCode, userId)
        }
    }

    @Test
    fun setPropertyEmail() {
        val email = "me@qonvesrion.io"
        every {
            repositoryMock.setProperty(QUserProperties.Email.userPropertyCode, email)
        } just Runs

        propertiesManagerMock.setProperty(QUserProperties.Email, email)

        verify(exactly = 1) {
            repositoryMock.setProperty(QUserProperties.Email.userPropertyCode, email)
        }
    }

    @Test
    fun setCustomProperty() {
        val key = "key"
        val value = "value"
        every {
            repositoryMock.setProperty(key, value)
        } just Runs

        propertiesManagerMock.setUserProperty(key, value)

        verify(exactly = 1) {
            repositoryMock.setProperty(key, value)
        }
    }
}