package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper.CacheConstants.CACHE_TIMESTAMP_KEY
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper.CacheConstants.DURATION_IN_HOURS_FOR_ACTUAL_CACHE
import com.qonversion.android.sdk.storage.LaunchResultCacheWrapper.CacheConstants.LAUNCH_RESULT_KEY
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import java.util.concurrent.TimeUnit

internal class LaunchResultCacheWrapper @Inject constructor(
    moshi: Moshi,
    private val cache: SharedPreferencesCache
) {
    private val jsonAdapter: JsonAdapter<QLaunchResult> =
        moshi.adapter(QLaunchResult::class.java)

    fun getActualLaunchResult(): QLaunchResult? {
        return if (isCacheOutdated()) {
            null
        } else {
            getLaunchResult()
        }
    }

    fun getLaunchResult(): QLaunchResult? {
        return cache.getObject(LAUNCH_RESULT_KEY, jsonAdapter)
    }

    fun save(launchResult: QLaunchResult) {
        cache.putObject(LAUNCH_RESULT_KEY, launchResult, jsonAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(CACHE_TIMESTAMP_KEY, currentTime)
    }

    private fun isCacheOutdated(): Boolean {
        val cachedTime = cache.getLong(CACHE_TIMESTAMP_KEY, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= TimeUnit.HOURS.toSeconds(DURATION_IN_HOURS_FOR_ACTUAL_CACHE)
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()

    private object CacheConstants {
        const val LAUNCH_RESULT_KEY = "launchResult"
        const val CACHE_TIMESTAMP_KEY = "timestamp"
        const val DURATION_IN_HOURS_FOR_ACTUAL_CACHE = 24L
    }
}
