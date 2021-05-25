package com.qonversion.android.sdk.services

import com.qonversion.android.sdk.Constants.PREFS_ORIGINAL_USER_ID_KEY
import com.qonversion.android.sdk.Constants.PREFS_USER_ID_KEY
import com.qonversion.android.sdk.Constants.USER_ID_PREFIX
import com.qonversion.android.sdk.Constants.USER_ID_SEPARATOR
import com.qonversion.android.sdk.storage.Cache
import com.qonversion.android.sdk.storage.TokenStorage
import java.util.*
import javax.inject.Inject

class QUserInfoService @Inject constructor(
    private val preferences: Cache,
    private val tokenStorage: TokenStorage
) {
    fun obtainUserID(): String {
        val cachedUserID = preferences.getString(PREFS_USER_ID_KEY, null)
        var resultUserID = cachedUserID

        if (resultUserID.isNullOrEmpty()) {
            resultUserID = tokenStorage.load()
            tokenStorage.delete()
        }

        if (resultUserID.isNullOrEmpty()) {
            resultUserID = generateRandomUserID()
        }

        if (cachedUserID.isNullOrEmpty()) {
            preferences.putString(PREFS_USER_ID_KEY, resultUserID)
            preferences.putString(PREFS_ORIGINAL_USER_ID_KEY, resultUserID)
        }

        return resultUserID
    }

    fun storeIdentity(userID: String) {
        preferences.putString(PREFS_USER_ID_KEY, userID)
    }

    fun logout() {
        val originalUserID = preferences.getString(PREFS_ORIGINAL_USER_ID_KEY, null)
        preferences.putString(PREFS_USER_ID_KEY, originalUserID)
    }

    fun deleteUser() {
        preferences.putString(PREFS_ORIGINAL_USER_ID_KEY, null)
        preferences.putString(PREFS_USER_ID_KEY, null)
        tokenStorage.delete()
    }

    // Private

    private fun generateRandomUserID(): String {
        val uuid = UUID.randomUUID().toString().replace(Regex("-"), "")
        val result = "$USER_ID_PREFIX$USER_ID_SEPARATOR$uuid"

        return result
    }
}