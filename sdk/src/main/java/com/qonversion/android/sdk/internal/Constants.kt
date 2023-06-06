package com.qonversion.android.sdk.internal

internal object Constants {
    const val PREFS_PREFIX = "com.qonversion.keys"
    const val PREFS_ORIGINAL_USER_ID_KEY = "$PREFS_PREFIX.originalUserID"
    const val PREFS_QONVERSION_USER_ID_KEY = "$PREFS_PREFIX.storedUserID"
    const val PREFS_PARTNER_IDENTITY_ID_KEY = "$PREFS_PREFIX.partnerIdentityUserID"
    const val PUSH_TOKEN_KEY = "$PREFS_PREFIX.push_token_key"
    const val PENDING_PUSH_TOKEN_KEY = "$PREFS_PREFIX.pending_push_token_key"
    const val USER_ID_PREFIX = "QON"
    const val USER_ID_SEPARATOR = "_"
    const val EXPERIMENT_STARTED_EVENT_NAME = "offering_within_experiment_called"
    const val IS_HISTORICAL_DATA_SYNCED = "$PREFS_PREFIX.is_historical_data_synced"
    const val INTERNAL_SERVER_ERROR_MIN = 500
    const val INTERNAL_SERVER_ERROR_MAX = 599
    const val PRICE_MICROS_DIVIDER: Double = 1000000.0
    const val CRASH_LOGS_URL = "https://sdk-logs.qonversion.io/sdk.log"
    const val CRASH_LOG_FILE_SUFFIX = ".qonversion.stacktrace"
}
