package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.daysToSeconds
import com.qonversion.android.sdk.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.dto.QEntitlementsCacheLifetime
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private const val LAUNCH_RESULT_KEY = "launchResult"
private const val PERMISSIONS_KEY = "last_loaded_permissions"
private const val LAUNCH_RESULT_CACHE_TIMESTAMP_KEY = "timestamp"
private const val PERMISSIONS_CACHE_TIMESTAMP_KEY = "permissions_timestamp"

internal class LaunchResultCacheWrapper(
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

    private var permissionsCacheLifetime = QEntitlementsCacheLifetime.MONTH

    val productPermissions get() = getLaunchResult()?.productPermissions

    var sessionLaunchResult: QLaunchResult? = null
        private set

    private var permissions: Map<String, QPermission>? = null

    fun setPermissionsCacheLifetime(lifetime: QEntitlementsCacheLifetime) {
        permissionsCacheLifetime = lifetime
    }

    fun getActualPermissions(): Map<String, QPermission>? {
        return permissions ?: if (isPermissionsCacheOutdated()) {
            null
        } else {
            getPermissions()
        }
    }

    fun clearPermissionsCache() {
        permissions = null
        cache.remove(PERMISSIONS_KEY)
    }

    fun getLaunchResult(): QLaunchResult? {
        return sessionLaunchResult ?: cache.getObject(LAUNCH_RESULT_KEY, launchResultAdapter)
    }

    private fun getPermissions(): Map<String, QPermission>? {
        if (permissions == null) {
            permissions = cache.getObject(PERMISSIONS_KEY, permissionsAdapter)
        }
        return permissions
    }

    fun save(launchResult: QLaunchResult) {
        sessionLaunchResult = launchResult
        cache.putObject(LAUNCH_RESULT_KEY, launchResult, launchResultAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(LAUNCH_RESULT_CACHE_TIMESTAMP_KEY, currentTime)

        this.permissions = launchResult.permissions
        savePermissions(launchResult.permissions)
    }

    fun updatePermissions(permissions: Map<String, QPermission>) {
        savePermissions(permissions)
    }

    private fun savePermissions(permissions: Map<String, QPermission>) {
        this.permissions = permissions
        cache.putObject(PERMISSIONS_KEY, permissions, permissionsAdapter)
        val currentTime = getCurrentTimeInSec()
        cache.putLong(PERMISSIONS_CACHE_TIMESTAMP_KEY, currentTime)
    }

    private fun isPermissionsCacheOutdated(): Boolean {
        return isCacheOutdated(PERMISSIONS_CACHE_TIMESTAMP_KEY, permissionsCacheLifetime.days.daysToSeconds)
    }

    @Suppress("SameParameterValue")
    private fun isCacheOutdated(timeKey: String, lifetimeSec: Long): Boolean {
        val cachedTime = cache.getLong(timeKey, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= lifetimeSec
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()
}
