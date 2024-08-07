package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.provider.CacheConfigProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.qonversion.android.sdk.internal.storage.Util.Companion.buildMoshi
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class LaunchResultCacheWrapperTest {
    private val mockPrefsCache: SharedPreferencesCache = mockk(relaxed = true)
    private val mockMoshi = buildMoshi()
    private val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)
    private val mockCacheConfigProvider = mockk<CacheConfigProvider>()
    private val mockQFallbacksService = mockk<QFallbacksService>()

    private lateinit var cacheWrapper: LaunchResultCacheWrapper

    private val launchResultKey = "launchResult"
    private val timestampKey = "timestamp"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        cacheWrapper = LaunchResultCacheWrapper(mockMoshi, mockPrefsCache, mockCacheConfigProvider, mockQFallbacksService)
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
