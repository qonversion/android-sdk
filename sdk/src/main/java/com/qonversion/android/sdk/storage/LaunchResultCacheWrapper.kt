package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

private const val LAUNCH_RESULT_KEY = "launchResult"
private const val LAUNCH_RESULT_CACHE_TIMESTAMP_KEY = "timestamp"

class LaunchResultCacheWrapper(
    moshi: Moshi,
    private val cache: SharedPreferencesCache
) {
    private val launchResultAdapter: JsonAdapter<QLaunchResult> =
        moshi.adapter(QLaunchResult::class.java)

    val productPermissions get() = getLaunchResult()?.productPermissions

    var sessionLaunchResult: QLaunchResult? = null
        private set

    fun getLaunchResult(): QLaunchResult? {
        return sessionLaunchResult ?: cache.getObject(LAUNCH_RESULT_KEY, launchResultAdapter)
    }

    fun save(launchResult: QLaunchResult) {
        sessionLaunchResult = launchResult
        cache.putObject(LAUNCH_RESULT_KEY, launchResult, launchResultAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(LAUNCH_RESULT_CACHE_TIMESTAMP_KEY, currentTime)
    }

    @Suppress("unused")
    private fun isCacheOutdated(timeKey: String, lifetimeSec: Long): Boolean {
        val cachedTime = cache.getLong(timeKey, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= lifetimeSec
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()
}
