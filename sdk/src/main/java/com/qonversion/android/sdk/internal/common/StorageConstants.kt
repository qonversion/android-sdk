package com.qonversion.android.sdk.internal.common

import com.qonversion.android.sdk.old.Constants

private const val PREFS_PREFIX = "com.qonversion.keys"

internal enum class StorageConstants(val key: String) {
    SourceKey("$PREFS_PREFIX.source"),
    VersionKey("$PREFS_PREFIX.sourceVersion"),
    UserId("${Constants.PREFS_PREFIX}.storedUserID"),
    OriginalUserId("${Constants.PREFS_PREFIX}.originalUserID"),
    Token("token_key") // Deprecated location of user id - was used in old sdk versions.
}
