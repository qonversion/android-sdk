package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.IOException
import java.util.concurrent.TimeUnit

class LaunchResultCache(
    private val preferences: SharedPreferences,
    val moshi: Moshi
) : DeviceCache<QLaunchResult, QLaunchResult?> {

    private val jsonAdapter: JsonAdapter<QLaunchResult> =
        moshi.adapter(QLaunchResult::class.java)

    override fun save(value: QLaunchResult) {
        saveLaunchResultAsJson(value)
        saveCurrentTime()
    }

    override fun load(): QLaunchResult? {
        if (isCacheOutdated()) {
            return null
        }

        val json = preferences.getString(LAUNCH_RESULT_KEY, "")
        if (json == null || json.isEmpty()) {
            return null
        }
        return try {
            val launchResult: QLaunchResult? = jsonAdapter.fromJson(json)
            launchResult
        } catch (e: IOException) {
            null
        }
    }

    private fun saveLaunchResultAsJson(value: QLaunchResult) {
        val jsonStr: String = jsonAdapter.toJson(value)
        preferences.edit().putString(LAUNCH_RESULT_KEY, jsonStr).apply()
    }

    private fun saveCurrentTime() {
        val currentTime = getCurrentTimeInSec()
        preferences.edit().putLong(CACHE_TIMESTAMP_KEY, currentTime).apply()
    }

    private fun isCacheOutdated(): Boolean {
        val cachedTime = preferences.getLong(CACHE_TIMESTAMP_KEY, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= TimeUnit.HOURS.toSeconds(DAY_DURATION_IN_HOURS)
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()

    companion object {
        private const val LAUNCH_RESULT_KEY = "launchResult"
        private const val CACHE_TIMESTAMP_KEY = "timestamp"
        private const val DAY_DURATION_IN_HOURS = 24L
    }

}