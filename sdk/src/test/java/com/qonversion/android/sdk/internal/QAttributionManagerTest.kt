package com.qonversion.android.sdk.internal

import android.os.Looper
import com.qonversion.android.sdk.dto.QAttributionSource
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.mockPrivateField
import io.mockk.*
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

internal class QAttributionManagerTest {
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val appStateProvider = mockk<AppStateProvider>()

    private lateinit var attributionManager: QAttributionManager

    private val fieldPendingAttrSource = "pendingAttributionSource"
    private val fieldPendingInfo = "pendingConversionInfo"
    private val conversionInfo = mapOf("key" to "value")

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        attributionManager = QAttributionManager(mockRepository, appStateProvider)
    }

    @Nested
    inner class Attribution {
        @Test
        fun `should send attribution on foreground`() {
            // given
            mockLooper()
            every {appStateProvider.appState} returns AppState.Foreground

            // when
            attributionManager.attribution(conversionInfo, QAttributionSource.AppsFlyer)

            // then
            verify(exactly = 1) {
                mockRepository.attribution(conversionInfo, QAttributionSource.AppsFlyer.id)
            }
        }

        @Test
        fun `should not send attribution on background`() {
            // given
            mockLooper()
            every {appStateProvider.appState} returns AppState.Background

            // when
            attributionManager.attribution(conversionInfo, QAttributionSource.AppsFlyer)

            // then
            val pendingSource =
                attributionManager.getPrivateField<QAttributionSource?>(fieldPendingAttrSource)
            val pendingInfo =
                attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't saved.",
                { Assert.assertEquals(QAttributionSource.AppsFlyer, pendingSource) },
                { Assert.assertEquals(pendingInfo, conversionInfo) }
            )

            verify {
                mockRepository wasNot called
            }
        }
    }

    @Nested
    inner class OnAppForeground {
        @Test
        fun `should send pending attribution after app switched to foreground`() {
            // given
            attributionManager.mockPrivateField(fieldPendingAttrSource, QAttributionSource.AppsFlyer)
            attributionManager.mockPrivateField(fieldPendingInfo, conversionInfo)

            // when
            attributionManager.onAppForeground()

            // then
            val pendingSource =
                attributionManager.getPrivateField<QAttributionSource?>(fieldPendingAttrSource)
            val pendingInfo =
                attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't cleared.",
                { Assert.assertEquals(null, pendingSource) },
                { Assert.assertEquals(null, pendingInfo) }
            )

            verify(exactly = 1) {
                mockRepository.attribution(conversionInfo, QAttributionSource.AppsFlyer.id)
            }
        }

        @Test
        fun `should not send null pending attribution after app switched to foreground`() {
            // given
            attributionManager.mockPrivateField(fieldPendingAttrSource, null)
            attributionManager.mockPrivateField(fieldPendingInfo, null)

            // when
            attributionManager.onAppForeground()

            // then
            val pendingSource =
                attributionManager.getPrivateField<QAttributionSource?>(fieldPendingAttrSource)
            val pendingInfo =
                attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't cleared.",
                { Assert.assertEquals(null, pendingSource) },
                { Assert.assertEquals(null, pendingInfo) }
            )

            verify {
                mockRepository wasNot called
            }
        }
    }

    private fun mockLooper() {
        val mockLooper = mockk<Looper>()
        mockkStatic(Looper::class)
        every {
            Looper.getMainLooper()
        } returns mockLooper
        every {
            Looper.myLooper()
        } returns mockLooper
    }
}

