package com.qonversion.android.sdk.internal.common.localStorage

import android.content.SharedPreferences

class SharedPreferencesStorage internal constructor(
    val preferences: SharedPreferences
): LocalStorage {
    override fun putInt(key: String, value: Int) {
        TODO("Not yet implemented")
    }

    override fun getInt(key: String, defValue: Int): Int {
        TODO("Not yet implemented")
    }

    override fun putFloat(key: String, value: Float) {
        TODO("Not yet implemented")
    }

    override fun getFloat(key: String, defValue: Float): Float {
        TODO("Not yet implemented")
    }

    override fun putLong(key: String, value: Long) {
        TODO("Not yet implemented")
    }

    override fun getLong(key: String, defValue: Long): Long {
        TODO("Not yet implemented")
    }

    override fun putString(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun getString(key: String, defValue: String): String {
        TODO("Not yet implemented")
    }
}