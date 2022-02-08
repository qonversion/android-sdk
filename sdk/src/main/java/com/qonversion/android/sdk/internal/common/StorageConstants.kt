package com.qonversion.android.sdk.internal.common

// todo don't forget to rename to `io.qonversion.keys` after renaming the package name
const val PREFS_NAME = "com.qonversion.keys"
private const val USER_PROPERTIES_PREFIX = "userProperties"

internal enum class StorageConstants(val key: String) {
    SourceKey("source"),
    VersionKey("sourceVersion"),
    UserId("storedUserID"),
    OriginalUserId("originalUserID"),
    Token("token_key"), // Deprecated location of user id - was used in old sdk versions.
    PendingUserProperties("$USER_PROPERTIES_PREFIX.pending"),
    SentUserProperties("$USER_PROPERTIES_PREFIX.sent")
}
