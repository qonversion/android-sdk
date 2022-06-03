package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.storage.Util.Companion.buildMoshi
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class LaunchResultCacheWrapperTest {
    private val mockPrefsCache: SharedPreferencesCache = mockk(relaxed = true)
    private val mockMoshi = buildMoshi()
    private val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)

    private lateinit var cacheWrapper: LaunchResultCacheWrapper

    private val launchResultKey = "launchResult"
    private val timestampKey = "timestamp"
    private val durationInHoursForActualCache = 24

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        cacheWrapper = LaunchResultCacheWrapper(mockMoshi, mockPrefsCache)
    }

    @Nested
    inner class Save {
        @Test
        fun `should save launchResult and timestamp in cache`() {
            val launchResult = Util.LAUNCH_RESULT

            cacheWrapper.save(launchResult)

            verifyOrder {
                mockPrefsCache.putObject(launchResultKey, launchResult, mockAdapter)
                mockPrefsCache.putLong(
                    timestampKey,
                    System.currentTimeMillis().milliSecondsToSeconds()
                )
            }
        }
    }

    @Nested
    inner class GetActualLaunchResult {
        @Test
        fun `should not load launchResult when cache is more than 24 hours old`() {
            mockOutdatedCacheTimestamp()

            val actualLaunchResult = cacheWrapper.getActualLaunchResult()

            verify(exactly = 1) {
                mockPrefsCache.getLong(timestampKey, 0)
            }
            verify(exactly = 0) {
                mockPrefsCache.getObject(launchResultKey, mockAdapter)
            }
            assertThat(actualLaunchResult).isNull()
        }

        @Test
        fun `should load launchResult when cache is less than 24 hours old`() {
            mockActualCacheTimestamp()
            val expectedLaunchResult = Util.LAUNCH_RESULT

            every {
                mockPrefsCache.getObject(launchResultKey, mockAdapter)
            } returns expectedLaunchResult

            val actualLaunchResult = cacheWrapper.getActualLaunchResult()

            verifyOrder {
                mockPrefsCache.getLong(timestampKey, 0)
                mockPrefsCache.getObject(launchResultKey, mockAdapter)
            }
            assertThat(actualLaunchResult).isEqualTo(expectedLaunchResult)
        }

        @Test
        fun `should return null when cache doesn't contain launchResult`() {
            mockActualCacheTimestamp()

            every {
                mockPrefsCache.getObject(launchResultKey, mockAdapter)
            } returns null

            val actualLaunchResult = cacheWrapper.getActualLaunchResult()

            verifyOrder {
                mockPrefsCache.getLong(timestampKey, 0)
                mockPrefsCache.getObject(launchResultKey, mockAdapter)
            }
            assertThat(actualLaunchResult).isNull()
        }

        private fun mockOutdatedCacheTimestamp() {
            mockCacheTimestamp(durationInHoursForActualCache)
        }

        private fun mockActualCacheTimestamp() {
            val durationInHours = 1
            mockCacheTimestamp(durationInHours)
        }

        private fun mockCacheTimestamp(durationInHours: Int) {
            val timeDiff = TimeUnit.HOURS.toSeconds(durationInHours.toLong())
            val currentTime = System.currentTimeMillis().milliSecondsToSeconds()
            val cachedTime = currentTime - timeDiff

            every {
                mockPrefsCache.getLong(timestampKey, any())
            } returns cachedTime
        }
    }
}