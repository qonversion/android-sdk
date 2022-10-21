package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.internal.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.internal.storage.Util.Companion.buildMoshi
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LaunchResultCacheWrapperTest {
    private val mockPrefsCache: SharedPreferencesCache = mockk(relaxed = true)
    private val mockMoshi = buildMoshi()
    private val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)

    private lateinit var cacheWrapper: LaunchResultCacheWrapper

    private val launchResultKey = "launchResult"
    private val timestampKey = "timestamp"

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
}