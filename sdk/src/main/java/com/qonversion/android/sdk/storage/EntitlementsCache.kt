package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.dto.QEntitlement
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import java.lang.reflect.Type

internal class EntitlementsCache @Inject constructor(
    moshi: Moshi,
    private val preferences: SharedPreferences
) {
    private val currentTimestamp: Long get() = System.currentTimeMillis() / 1000

    private val collectionEntitlementsType: Type = Types.newParameterizedType(
        List::class.java,
        QEntitlement::class.java
    )
    private val jsonAdapter: JsonAdapter<List<QEntitlement>> =
        moshi.adapter(collectionEntitlementsType)

    fun getStoredValue(): List<QEntitlement>? {

        val json = preferences.getString(ENTITLEMENTS_KEY, null) ?: return null
        return jsonAdapter.fromJson(json)
    }

    fun getActualStoredValue(isError: Boolean = false): List<QEntitlement>? {
        val savingTime = preferences.getLong(SAVING_TIME_KEY, DEFAULT_SAVING_TIME)
            .takeIf { it != DEFAULT_SAVING_TIME } ?: return null

        val lifetime = currentTimestamp - savingTime
        val availableLifetime = if (isError) MAX_LIFETIME_ON_ERROR_MS else MAX_LIFETIME_MS
        if (availableLifetime < lifetime) {
            return null
        }

        return getStoredValue()
    }

    fun store(entitlements: List<QEntitlement>) {
        val json = jsonAdapter.toJson(entitlements)
        preferences.edit()
            .putString(ENTITLEMENTS_KEY, json)
            .putLong(SAVING_TIME_KEY, currentTimestamp)
            .apply()
    }

    fun reset() {
        preferences.edit()
            .remove(ENTITLEMENTS_KEY)
            .remove(SAVING_TIME_KEY)
            .apply()
    }

    companion object {
        private const val ENTITLEMENTS_KEY = "qonversion_entitlements"
        private const val SAVING_TIME_KEY = "qonversion_entitlements_save_time"
        private const val DEFAULT_SAVING_TIME = -1L
        private const val MAX_LIFETIME_MS = 1000 * 60 * 5
        private const val MAX_LIFETIME_ON_ERROR_MS = 1000 * 60 * 60 * 24
    }
}