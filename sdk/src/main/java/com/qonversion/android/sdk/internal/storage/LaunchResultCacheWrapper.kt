package com.qonversion.android.sdk.internal.storage

import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.internal.milliSecondsToSeconds
import com.qonversion.android.sdk.internal.daysToSeconds
import com.qonversion.android.sdk.internal.dto.QLaunchResult
import com.qonversion.android.sdk.internal.dto.QPermission
import com.qonversion.android.sdk.internal.provider.CacheConfigProvider
import com.qonversion.android.sdk.internal.services.QFallbacksService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private const val LAUNCH_RESULT_KEY = "launchResult"
private const val PERMISSIONS_KEY = "last_loaded_permissions"
private const val LAUNCH_RESULT_CACHE_TIMESTAMP_KEY = "timestamp"
private const val PERMISSIONS_CACHE_TIMESTAMP_KEY = "permissions_timestamp"

internal class LaunchResultCacheWrapper(
    moshi: Moshi,
    private val cache: SharedPreferencesCache,
    private val cacheConfigProvider: CacheConfigProvider,
    private val fallbacksService: QFallbacksService
) {
    private val launchResultAdapter: JsonAdapter<QLaunchResult> =
        moshi.adapter(QLaunchResult::class.java)
    private val fallbackData: QFallbackObject? by lazy {
        fallbacksService.obtainFallbackData()
    }

    private val permissionsAdapter: JsonAdapter<Map<String, QPermission>> =
        moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                QPermission::class.java
            )
        )

    var sessionLaunchResult: QLaunchResult? = null
        private set

    private var permissions: Map<String, QPermission>? = null

    fun getActualPermissions(): Map<String, QPermission>? {
        return permissions ?: if (isPermissionsCacheOutdated()) {
            null
        } else {
            getPermissions()
        }
    }

    fun resetSessionCache() {
        sessionLaunchResult = null
        permissions = null
    }

    fun clearPermissionsCache() {
        permissions = null
        cache.remove(PERMISSIONS_KEY)
    }

    private fun getLaunchResult(): QLaunchResult? {
        return sessionLaunchResult ?: cache.getObject(LAUNCH_RESULT_KEY, launchResultAdapter)
    }

    fun getActualProducts(): Map<String, QProduct>? {
        val products = getLaunchResult()?.products ?: fallbackData?.products

        return products
    }

    fun getProductPermissions(): Map<String, List<String>>? {
        val productPermissions = getLaunchResult()?.productPermissions ?: fallbackData?.productPermissions

        return productPermissions
    }

    fun getActualOfferings(): QOfferings? {
        val offerings = getLaunchResult()?.offerings ?: fallbackData?.offerings

        return offerings
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
        val cacheLifetime = cacheConfigProvider.cacheConfig.entitlementsCacheLifetime
        return isCacheOutdated(PERMISSIONS_CACHE_TIMESTAMP_KEY, cacheLifetime.days.daysToSeconds)
    }

    @Suppress("SameParameterValue")
    private fun isCacheOutdated(timeKey: String, lifetimeSec: Long): Boolean {
        val cachedTime = cache.getLong(timeKey, 0)
        val currentTime = getCurrentTimeInSec()

        return currentTime - cachedTime >= lifetimeSec
    }

    private fun getCurrentTimeInSec() = System.currentTimeMillis().milliSecondsToSeconds()
}
