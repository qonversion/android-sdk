package com.qonversion.android.sdk

import android.os.Looper
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QAttributionManagerTest {
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)

    private lateinit var attributionManager: QAttributionManager

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
            val key = "key"
            val value = "value"
            val conversionInfo = mutableMapOf<String, String>()
            conversionInfo[key] = value

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
            val key = "key"
            val value = "value"
            val conversionInfo = mutableMapOf<String, String>()
            conversionInfo[key] = value

            mockLooper()
            Qonversion.appState = AppState.Background

            // when
            attributionManager.attribution(conversionInfo, AttributionSource.AppsFlyer)

            // then
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