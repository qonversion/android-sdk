package com.qonversion.android.sdk.storage

import android.content.SharedPreferences

class TokenStorage(private val preferences: SharedPreferences) : Storage {

    companion object {
        private const val TOKEN_KEY = "token_key"
    }

    override fun save(token: String) {
       preferences.edit().putString(TOKEN_KEY, token).apply()
    }

    override fun load(): String {
        return preferences.let { it.getString(TOKEN_KEY, "") } ?: ""
    }

    override fun exist(): Boolean {
        return !preferences.getString(TOKEN_KEY, "").isNullOrEmpty()
    }

    override fun delete() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

}