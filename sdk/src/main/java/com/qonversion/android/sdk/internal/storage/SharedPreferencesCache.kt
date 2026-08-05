package com.qonversion.android.sdk.internal.storage

import android.content.SharedPreferences

import com.squareup.moshi.JsonAdapter
import java.io.IOException

internal class SharedPreferencesCache(
    private val preferences: SharedPreferences
) : Cache {

    override fun putInt(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    override fun getInt(key: String, defValue: Int): Int = preferences.getInt(key, defValue)

    override fun getBool(key: String, defValue: Boolean): Boolean = preferences.getBoolean(key, defValue)

    override fun putBool(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()

    override fun putFloat(key: String, value: Float) =
        preferences.edit().putFloat(key, value).apply()

    override fun getFloat(key: String, defValue: Float): Float =
        preferences.getFloat(key, defValue)

    override fun putLong(key: String, value: Long) =
        preferences.edit().putLong(key, value).apply()

    override fun getLong(key: String, defValue: Long): Long = preferences.getLong(key, defValue)

    override fun putString(key: String, value: String?) =
        preferences.edit().putString(key, value).apply()

    override fun updateStrings(values: Map<String, String?>, removedKeys: Set<String>) {
        preferences.edit().also { editor ->
            removedKeys.forEach { key -> editor.remove(key) }
            values.forEach { (key, value) -> editor.putString(key, value) }
        }.apply()
    }

    @Suppress("TooGenericExceptionCaught") // Any runtime commit failure needs the same in-memory rollback.
    override fun updateStringsDurably(values: Map<String, String?>, removedKeys: Set<String>): Boolean {
        val affectedKeys = values.keys + removedKeys
        val previousValues = affectedKeys.associateWith { key ->
            val exists = preferences.contains(key)
            exists to if (exists) preferences.getString(key, null) else null
        }
        val committed = try {
            preferences.edit().also { editor ->
                removedKeys.forEach { key -> editor.remove(key) }
                values.forEach { (key, value) -> editor.putString(key, value) }
            }.commit()
        } catch (error: RuntimeException) {
            restoreStrings(previousValues)
            throw error
        }
        if (!committed) restoreStrings(previousValues)
        return committed
    }

    private fun restoreStrings(previousValues: Map<String, Pair<Boolean, String?>>) {
        preferences.edit().also { editor ->
            previousValues.forEach { (key, previous) ->
                if (previous.first) {
                    editor.putString(key, previous.second)
                } else {
                    editor.remove(key)
                }
            }
        }.apply()
    }

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

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
