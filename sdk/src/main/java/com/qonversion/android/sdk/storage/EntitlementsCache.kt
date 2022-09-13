package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.Constants.PREFS_OLD_PERMISSIONS_KEY
import com.qonversion.android.sdk.Constants.PREFS_ENTITLEMENTS
import com.qonversion.android.sdk.Constants.PREFS_ENTITLEMENTS_SAVING_TIME
import com.qonversion.android.sdk.Constants.PREFS_OLD_PERMISSIONS_CACHE_TIMESTAMP_KEY
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import com.qonversion.android.sdk.daysToSeconds
import com.qonversion.android.sdk.dto.QEntitlement
import com.qonversion.android.sdk.dto.QEntitlementCacheLifetime
import com.qonversion.android.sdk.dto.QPermission
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject

internal class EntitlementsCache @Inject constructor(
    moshi: Moshi,
    private val preferencesCache: SharedPreferencesCache
) {
    private val currentTimeSec: Long get() = System.currentTimeMillis().milliSecondsToSeconds()
    private var cacheLifetimeOnError = QEntitlementCacheLifetime.MONTH

    private val jsonAdapter: JsonAdapter<List<QEntitlement>> =
        moshi.adapter(
            Types.newParameterizedType(
                List::class.java,
                QEntitlement::class.java
            )
        )

    private val oldPermissionsAdapter: JsonAdapter<Map<String, QPermission>> =
        moshi.adapter(
            Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                QPermission::class.java
            )
        )

    fun setCacheLifetime(lifetime: QEntitlementCacheLifetime) {
        cacheLifetimeOnError = lifetime
    }

    fun getStoredValue(): List<QEntitlement>? {
        return preferencesCache.getObject(PREFS_ENTITLEMENTS, jsonAdapter)
            ?: getOldPermissionsValue()
    }

    fun getActualStoredValue(isError: Boolean = false): List<QEntitlement>? {
        val savingTimeSec = preferencesCache.getLong(PREFS_ENTITLEMENTS_SAVING_TIME, DEFAULT_SAVING_TIME)
            .takeIf { it != DEFAULT_SAVING_TIME } ?: return null

        val lifetime = currentTimeSec - savingTimeSec
        val availableLifetime = if (isError) {
            cacheLifetimeOnError.days.daysToSeconds
        } else {
            MAX_LIFETIME_SEC
        }
        if (availableLifetime < lifetime) {
            return null
        }

        return getStoredValue()
    }

    fun store(entitlements: List<QEntitlement>) {
        preferencesCache.putObject(PREFS_ENTITLEMENTS, entitlements, jsonAdapter)
        preferencesCache.putLong(PREFS_ENTITLEMENTS_SAVING_TIME, currentTimeSec)
        preferencesCache.remove(PREFS_OLD_PERMISSIONS_KEY)
        preferencesCache.remove(PREFS_OLD_PERMISSIONS_CACHE_TIMESTAMP_KEY)
    }

    fun reset() {
        preferencesCache.remove(PREFS_ENTITLEMENTS)
        preferencesCache.remove(PREFS_ENTITLEMENTS_SAVING_TIME)
        preferencesCache.remove(PREFS_OLD_PERMISSIONS_KEY)
        preferencesCache.remove(PREFS_OLD_PERMISSIONS_CACHE_TIMESTAMP_KEY)
    }

    private fun getOldPermissionsValue(): List<QEntitlement>? {
        return preferencesCache.getObject(PREFS_OLD_PERMISSIONS_KEY, oldPermissionsAdapter)
            ?.values
            ?.map { QEntitlement(it) }
    }

    companion object {
        private const val DEFAULT_SAVING_TIME = -1L
        private const val MAX_LIFETIME_SEC = 60L * 5
    }
}