package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.*
import com.squareup.moshi.Moshi
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

internal class LaunchResultCacheTest {
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val mockMoshi = buildMoshi()

    private lateinit var launchResultCache: LaunchResultCache

    private val launchResultKey = "launchResult"
    private val cacheTimestampKey = "timestamp"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockSharedPreferences()

        launchResultCache = LaunchResultCache(mockPrefs, mockMoshi)
    }

    @Nested
    inner class Save {
        @Test
        fun `should save launchResult and timestamp in cache`() {
            val launchResult = Util.LAUNCH_RESULT
            val launchResultJsonStr = Util.LAUNCH_RESULT_JSON_STR

            launchResultCache.save(launchResult)

            verifyOrder {
                mockEditor.putString(launchResultKey, launchResultJsonStr)
                mockEditor.apply()
                mockEditor.putLong(
                    cacheTimestampKey,
                    System.currentTimeMillis().milliSecondsToSeconds()
                )
                mockEditor.apply()
            }
        }
    }

    @Nested
    inner class Load {
        @Test
        fun `should not load launchResult when cache is more than 24 hours old`() {
            mockOutdatedCacheTimestamp()

            val launchResult = launchResultCache.load()

            verify(exactly = 1) {
                mockPrefs.getLong(cacheTimestampKey, any())
            }
            verify(exactly = 0) {
                mockPrefs.getString(launchResultKey, any())
            }
            assertThat(launchResult).isNull()
        }

        @Test
        fun `should load launchResult when cache is less than 24 hours old`() {
            mockActualCacheTimestamp()
            val launchResult = Util.LAUNCH_RESULT
            val launchResultJsonStr = Util.LAUNCH_RESULT_JSON_STR

            every {
                mockPrefs.getString(launchResultKey, any())
            } returns launchResultJsonStr

            val expectedLaunchResult = launchResultCache.load()

            verify(exactly = 1) {
                mockPrefs.getString(launchResultKey, "")
            }
            assertThat(expectedLaunchResult).isEqualTo(launchResult)
        }

        @Test
        fun `should return null when cache doesn't contain launchResult`() {
            mockActualCacheTimestamp()

            val expectedLaunchResult = launchResultCache.load()

            verify(exactly = 1) {
                mockPrefs.getString(launchResultKey, "")
            }
            assertThat(expectedLaunchResult).isNull()
        }

        @Test
        fun `should return null when sharedPreferences returns invalid json string`() {
            mockActualCacheTimestamp()

            val invalidLaunchResultJsonStr = "Invalid LaunchResult Json Str"
            every {
                mockPrefs.getString(launchResultKey, any())
            } returns invalidLaunchResultJsonStr

            val expectedLaunchResult = launchResultCache.load()

            verify(exactly = 1) {
                mockPrefs.getString(launchResultKey, "")
            }
            assertThat(expectedLaunchResult).isNull()
        }

        private fun mockOutdatedCacheTimestamp() {
            val durationInHours = 24
            mockCacheTimestamp(durationInHours)
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
                mockPrefs.getLong(cacheTimestampKey, any())
            } returns cachedTime
        }
    }

    private fun buildMoshi() =
        Moshi.Builder()
            .add(QProductDurationAdapter())
            .add(QDateAdapter())
            .add(QProductsAdapter())
            .add(QPermissionsAdapter())
            .add(QProductTypeAdapter())
            .add(QProductRenewStateAdapter())
            .add(QOfferingsAdapter())
            .add(QOfferingTagAdapter())
            .add(QExperimentGroupTypeAdapter())
            .add(QExperimentsAdapter())
            .add(QEligibilityStatusAdapter())
            .add(QEligibilityAdapter())
            .build()

    private fun mockSharedPreferences() {
        every {
            mockEditor.putString(launchResultKey, any())
        } returns mockEditor
        every {
            mockEditor.putLong(cacheTimestampKey, any())
        } returns mockEditor

        every {
            mockPrefs.edit()
        } returns mockEditor

        every {
            mockEditor.apply()
        } just runs
    }
}
