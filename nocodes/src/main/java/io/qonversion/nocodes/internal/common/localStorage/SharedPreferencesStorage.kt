package io.qonversion.nocodes.internal.common.localStorage

import android.content.SharedPreferences

internal class SharedPreferencesStorage(
    private val preferences: SharedPreferences
) : LocalStorage {
    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defValue: Int): Int = preferences.getInt(key, defValue)

    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }

    override fun getFloat(key: String, defValue: Float): Float = preferences.getFloat(key, defValue)

    override fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long = preferences.getLong(key, defValue)

    override fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return preferences.getString(key, defValue)
    }

    override fun remove(key: String) {
        return preferences.edit().remove(key).apply()
    }
}