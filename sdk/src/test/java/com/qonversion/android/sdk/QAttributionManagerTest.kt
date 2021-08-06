package com.qonversion.android.sdk

import android.os.Looper
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
            val pendingSource = attributionManager.getPrivateField<AttributionSource?>(fieldPendingAttrSource)
            val pendingInfo = attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't saved.",
                { Assert.assertEquals(AttributionSource.AppsFlyer, pendingSource) },
                { Assert.assertEquals(pendingInfo, conversionInfo) }
            )

            verify {
                mockRepository wasNot called
            }
        }

        @Test
        fun `should send attribution after app switched from background to foreground`(){
            // given
            mockLooper()
            Qonversion.appState = AppState.Background

            // when
            attributionManager.attribution(conversionInfo, AttributionSource.AppsFlyer)
            attributionManager.onAppForeground()

            // then
            val pendingSource = attributionManager.getPrivateField<AttributionSource?>(fieldPendingAttrSource)
            val pendingInfo = attributionManager.getPrivateField<Map<String, Any>?>(fieldPendingInfo)
            assertAll(
                "Pending attribution info wasn't cleared.",
                { Assert.assertEquals(null, pendingSource) },
                { Assert.assertEquals(null, pendingInfo) }
            )

            verify(exactly = 1) {
                mockRepository.attribution(conversionInfo, AttributionSource.AppsFlyer.id)
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