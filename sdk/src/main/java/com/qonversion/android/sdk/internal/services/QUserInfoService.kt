package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.internal.Constants.PREFS_PARTNER_IDENTITY_ID_KEY
import com.qonversion.android.sdk.internal.Constants.PREFS_ORIGINAL_USER_ID_KEY
import com.qonversion.android.sdk.internal.Constants.PREFS_QONVERSION_USER_ID_KEY
import com.qonversion.android.sdk.internal.Constants.USER_ID_PREFIX
import com.qonversion.android.sdk.internal.Constants.USER_ID_SEPARATOR
import com.qonversion.android.sdk.internal.storage.Cache
import com.qonversion.android.sdk.internal.storage.TokenStorage
import java.util.UUID

import javax.inject.Inject

internal class QUserInfoService @Inject constructor(
    private val preferences: Cache,
    private val tokenStorage: TokenStorage
) {
    fun obtainUserID(): String {
        val cachedUserID = preferences.getString(PREFS_QONVERSION_USER_ID_KEY, null)
        var resultUserID = cachedUserID

        if (resultUserID.isNullOrEmpty()) {
            resultUserID = tokenStorage.load()
            tokenStorage.delete()
        }

        if (resultUserID.isNullOrEmpty() || resultUserID == TEST_UID) {
            resultUserID = generateRandomUserID()
        }

        if (cachedUserID.isNullOrEmpty() || cachedUserID == TEST_UID) {
            preferences.putString(PREFS_QONVERSION_USER_ID_KEY, resultUserID)
            preferences.putString(PREFS_ORIGINAL_USER_ID_KEY, resultUserID)
        }

        return resultUserID
    }

    fun storeQonversionUserId(userID: String) {
        preferences.putString(PREFS_QONVERSION_USER_ID_KEY, userID)
    }

    fun storePartnersIdentityId(userID: String) {
        preferences.putString(PREFS_PARTNER_IDENTITY_ID_KEY, userID)
    }

    fun getPartnersIdentityId(): String? {
        return preferences.getString(PREFS_PARTNER_IDENTITY_ID_KEY, null)
    }

    fun logoutIfNeeded(): Boolean {
        val originalUserId = preferences.getString(PREFS_ORIGINAL_USER_ID_KEY, null)
        val currentUserId = preferences.getString(PREFS_QONVERSION_USER_ID_KEY, null)

        preferences.putString(PREFS_PARTNER_IDENTITY_ID_KEY, null)

        if (originalUserId == currentUserId) {
            return false
        }

        preferences.putString(PREFS_QONVERSION_USER_ID_KEY, originalUserId)

        return true
    }

    fun deleteUser() {
        preferences.putString(PREFS_ORIGINAL_USER_ID_KEY, null)
        preferences.putString(PREFS_QONVERSION_USER_ID_KEY, null)
        preferences.putString(PREFS_PARTNER_IDENTITY_ID_KEY, null)
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
