package com.qonversion.android.sdk.services

import com.qonversion.android.sdk.Constants.PREFS_ORIGINAL_USER_ID_KEY
import com.qonversion.android.sdk.Constants.PREFS_USER_ID_KEY
import com.qonversion.android.sdk.Constants.USER_ID_PREFIX
import com.qonversion.android.sdk.Constants.USER_ID_SEPARATOR
import com.qonversion.android.sdk.storage.Cache
import com.qonversion.android.sdk.storage.TokenStorage
import java.util.UUID

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

        if (resultUserID.isNullOrEmpty() || resultUserID == TEST_UID) {
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

    fun logoutIfNeeded(): Boolean {
        val originalUserID = preferences.getString(PREFS_ORIGINAL_USER_ID_KEY, null)
        val defaultUserID = preferences.getString(PREFS_USER_ID_KEY, null)

        if (originalUserID == defaultUserID) {
            return false
        }

        preferences.putString(PREFS_USER_ID_KEY, originalUserID)

        return true
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

    companion object {
        const val TEST_UID = "40egafre6_e_"
    }
}
