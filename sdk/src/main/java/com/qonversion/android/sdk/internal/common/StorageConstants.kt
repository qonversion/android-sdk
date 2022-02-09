package com.qonversion.android.sdk.internal.common

const val PREFS_NAME = "io.qonversion.keys"
private const val USER_PROPERTIES_PREFIX = "userProperties"

internal enum class StorageConstants(val key: String) {
    SourceKey("source"),
    VersionKey("sourceVersion"),
    UserId("storedUserID"),
    OriginalUserId("originalUserID"),
    Token("token_key"), // todo do not forget to remove this key after the migrator implementation
    PendingUserProperties("$USER_PROPERTIES_PREFIX.pending"),
    SentUserProperties("$USER_PROPERTIES_PREFIX.sent")
}
