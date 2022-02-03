package com.qonversion.android.sdk.internal.common

private const val PREFS_PREFIX = "com.qonversion.keys"
private const val USER_PROPERTIES_PREFIX = "userProperties"

internal enum class StorageConstants(val key: String) {
    SourceKey("$PREFS_PREFIX.source"),
    VersionKey("$PREFS_PREFIX.sourceVersion"),
    UserId("$PREFS_PREFIX.storedUserID"),
    OriginalUserId("$PREFS_PREFIX.originalUserID"),
    Token("token_key"), // Deprecated location of user id - was used in old sdk versions.
    PendingUserProperties("$PREFS_PREFIX.$USER_PROPERTIES_PREFIX.pending"),
    SentUserProperties("$PREFS_PREFIX.$USER_PROPERTIES_PREFIX.sent")
}
