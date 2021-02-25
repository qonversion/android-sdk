package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.storage.CustomUidStorage.Constants.CUSTOM_UID_KEY

class CustomUidStorage(private val sharedPreferencesCache: SharedPreferencesCache) {

    private object Constants {
        const val CUSTOM_UID_KEY = "custom_uid_key"
    }

    fun load() = sharedPreferencesCache.getString(CUSTOM_UID_KEY, null)

    fun save(value: String) = sharedPreferencesCache.putString(CUSTOM_UID_KEY, value)

}