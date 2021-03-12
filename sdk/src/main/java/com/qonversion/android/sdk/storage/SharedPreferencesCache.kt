package com.qonversion.android.sdk.storage

import android.content.SharedPreferences

import com.squareup.moshi.JsonAdapter
import java.io.IOException

class SharedPreferencesCache(
    private val preferences: SharedPreferences
) : Cache {

    override fun putInt(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    override fun getInt(key: String, defValue: Int): Int = preferences.getInt(key, defValue)

    override fun putFloat(key: String, value: Float) =
        preferences.edit().putFloat(key, value).apply()

    override fun getFloat(key: String, defValue: Float): Float =
        preferences.getFloat(key, defValue)

    override fun putLong(key: String, value: Long) =
        preferences.edit().putLong(key, value).apply()

    override fun getLong(key: String, defValue: Long): Long = preferences.getLong(key, defValue)

    override fun putString(key: String, value: String?) =
        preferences.edit().putString(key, value).apply()

    override fun getString(key: String, defValue: String?): String? =
        preferences.getString(key, defValue)

    override fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>) {
        val jsonStr: String = adapter.toJson(value)
        putString(key, jsonStr)
    }

    override fun <T> getObject(key: String, adapter: JsonAdapter<T>): T? {
        val jsonStr = getString(key, "")
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null
        }
        return try {
            adapter.fromJson(jsonStr)
        } catch (e: IOException) {
            null
        }
    }
}