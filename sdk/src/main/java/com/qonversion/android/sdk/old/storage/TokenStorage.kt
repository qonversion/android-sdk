package com.qonversion.android.sdk.old.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.old.validator.Validator

class TokenStorage(
    private val preferences: SharedPreferences,
    private val tokenValidator: Validator<String>
) : Storage {

    companion object {
        private const val TOKEN_KEY = "token_key"
    }

    override fun save(token: String) {
       if (tokenValidator.valid(token)) {
           preferences.edit().putString(TOKEN_KEY, token).apply()
       }
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
