package com.qonversion.android.sdk.internal

import android.os.Looper
import com.qonversion.android.sdk.AttributionSource
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.getPrivateField
import com.qonversion.android.sdk.mockPrivateField
import io.mockk.*
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class QAttributionManagerTest {
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)

    private lateinit var attributionManager: QAttributionManager

    private val fieldPendingAttrSource = "pendingAttributionSource"
    private val fieldPendingInfo = "pendingConversionInfo"
    private val conversionInfo = mapOf("key" to "value")

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        attributionManager = QAttributionManager(mockRepository)
    }

    @Nested
    inner class Attribution {
        @Test
        fun `should send attribution on foreground`() {
            // given
            mockLooper()
            Qonversion.appState = AppState.Foreground

            // when
            attributionManager.attribution(conversionInfo, AttributionSource.AppsFlyer)

            // then
            verify(exactly = 1) {
                mockRepository.attribution(conversionInfo, AttributionSource.AppsFlyer.id)
            }
        }

        @Test
        fun `should not send attribution on background`() {
            // given
            mockLooper()
            Qonversion.appState = AppState.Background

            // when
            attributionManager.attribution(conversionInfo, AttributionSource.AppsFlyer)

            // then
            val pendingSource =
                attributionManager.getPrivateField<AttributionSource?>(fieldPendingAttrSource)
            val pendingInfo =
                attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't saved.",
                { Assert.assertEquals(AttributionSource.AppsFlyer, pendingSource) },
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
            attributionManager.mockPrivateField(fieldPendingAttrSource, AttributionSource.AppsFlyer)
            attributionManager.mockPrivateField(fieldPendingInfo, conversionInfo)

            // when
            attributionManager.onAppForeground()

            // then
            val pendingSource =
                attributionManager.getPrivateField<AttributionSource?>(fieldPendingAttrSource)
            val pendingInfo =
                attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't cleared.",
                { Assert.assertEquals(null, pendingSource) },
                { Assert.assertEquals(null, pendingInfo) }
            )

            verify(exactly = 1) {
                mockRepository.attribution(conversionInfo, AttributionSource.AppsFlyer.id)
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
                attributionManager.getPrivateField<AttributionSource?>(fieldPendingAttrSource)
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
