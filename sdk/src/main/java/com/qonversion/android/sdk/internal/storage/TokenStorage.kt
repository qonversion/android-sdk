package com.qonversion.android.sdk.internal.storage

import android.content.SharedPreferences

internal class TokenStorage(private val preferences: SharedPreferences) : Storage {

    companion object {
        private const val TOKEN_KEY = "token_key"
    }

    override fun load(): String {
        return preferences.getString(TOKEN_KEY, "") ?: ""
    }

    override fun delete() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }
}
