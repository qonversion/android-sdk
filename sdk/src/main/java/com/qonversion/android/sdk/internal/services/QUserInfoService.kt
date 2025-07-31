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
    fun obtainUserId(): String {
        val cachedUserId = preferences.getString(PREFS_QONVERSION_USER_ID_KEY, null)
        var resultUserId = cachedUserId

        if (resultUserId.isNullOrEmpty()) {
            resultUserId = tokenStorage.load()
            tokenStorage.delete()
        }

        if (resultUserId.isEmpty() || resultUserId == TEST_UID) {
            resultUserId = generateRandomUserId()
        }

        if (cachedUserId.isNullOrEmpty() || cachedUserId == TEST_UID) {
            preferences.putString(PREFS_QONVERSION_USER_ID_KEY, resultUserId)
            preferences.putString(PREFS_ORIGINAL_USER_ID_KEY, resultUserId)
        }

        return resultUserId
    }

    fun storeQonversionUserId(userId: String) {
        preferences.putString(PREFS_QONVERSION_USER_ID_KEY, userId)
    }

    fun storePartnersIdentityId(userId: String) {
        preferences.putString(PREFS_PARTNER_IDENTITY_ID_KEY, userId)
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

    private fun generateRandomUserId(): String {
        val uuid = UUID.randomUUID().toString().replace(Regex("-"), "")
        val result = "$USER_ID_PREFIX$USER_ID_SEPARATOR$uuid"

        return result
    }

    companion object {
        const val TEST_UID = "40egafre6_e_"
    }
}
