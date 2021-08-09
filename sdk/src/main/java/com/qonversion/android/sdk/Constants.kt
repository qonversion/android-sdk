package com.qonversion.android.sdk

object Constants {
    const val PREFS_PREFIX = "com.qonversion.keys"
    const val PREFS_ORIGINAL_USER_ID_KEY = "$PREFS_PREFIX.originalUserID"
    const val PREFS_USER_ID_KEY = "$PREFS_PREFIX.storedUserID"
    const val PUSH_TOKEN_KEY = "$PREFS_PREFIX.push_token_key"
    const val PENDING_PUSH_TOKEN_KEY = "$PREFS_PREFIX.pending_push_token_key"
    const val USER_ID_PREFIX = "QON"
    const val USER_ID_SEPARATOR = "_"
    const val EXPERIMENT_STARTED_EVENT_NAME = "offering_within_experiment_called"
}