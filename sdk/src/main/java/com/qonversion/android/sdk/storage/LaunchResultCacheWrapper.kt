package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.daysToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.dto.QPermission
import com.qonversion.android.sdk.dto.QPermissionsCacheLifetime
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private const val LAUNCH_RESULT_KEY = "launchResult"
private const val PERMISSIONS_KEY = "last_loaded_permissions"
private const val LAUNCH_RESULT_CACHE_TIMESTAMP_KEY = "timestamp"
private const val PERMISSIONS_CACHE_TIMESTAMP_KEY = "permissions_timestamp"

class LaunchResultCacheWrapper(
    moshi: Moshi,
    private val cache: SharedPreferencesCache
) {
    private val launchResultAdapter: JsonAdapter<QLaunchResult> =
        moshi.adapter(QLaunchResult::class.java)

    private val permissionsAdapter: JsonAdapter<Map<String, QPermission>> =
        moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                QPermission::class.java
            )
        )

    private var permissionsCacheLifetime = QPermissionsCacheLifetime.MONTH

    fun setPermissionsCacheLifetime(lifetime: QPermissionsCacheLifetime) {
        permissionsCacheLifetime = lifetime
    }

    fun getActualPermissions(): Map<String, QPermission>? {
        return if (isCacheOutdated(permissionsCacheLifetime.days.daysToSeconds)) {
            null
        } else {
            getPermissions()
        }
    }

    fun clearPermissionsCache() {
        cache.remove(PERMISSIONS_KEY)
    }

    fun getLaunchResult(): QLaunchResult? {
        return cache.getObject(LAUNCH_RESULT_KEY, launchResultAdapter)
    }

    private fun getPermissions(): Map<String, QPermission>? {
        return cache.getObject(PERMISSIONS_KEY, permissionsAdapter)
    }

    fun save(launchResult: QLaunchResult) {
        cache.putObject(LAUNCH_RESULT_KEY, launchResult, launchResultAdapter)
        cache.putObject(PERMISSIONS_KEY, launchResult.permissions, permissionsAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(LAUNCH_RESULT_CACHE_TIMESTAMP_KEY, currentTime)
        cache.putLong(PERMISSIONS_CACHE_TIMESTAMP_KEY, currentTime)
    }

    fun updatePermissions(permissions: Map<String, QPermission>) {
        cache.putObject(PERMISSIONS_KEY, permissions, permissionsAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(PERMISSIONS_CACHE_TIMESTAMP_KEY, currentTime)
    }

    private fun isCacheOutdated(timeIsSec: Long): Boolean {
        val cachedTime = cache.getLong(LAUNCH_RESULT_CACHE_TIMESTAMP_KEY, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= timeIsSec
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()
}
