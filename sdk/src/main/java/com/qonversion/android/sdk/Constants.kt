package com.qonversion.android.sdk

object Constants {
    const val PREFS_PREFIX = "com.qonversion.keys"
    const val PREFS_QONVERSION_USER_ID_KEY = "$PREFS_PREFIX.storedUserID"
    const val PREFS_CUSTOM_USER_ID_KEY = "$PREFS_PREFIX.customUserID"
    const val PREFS_ENTITLEMENTS = "$PREFS_PREFIX.entitlements"
    const val PREFS_ENTITLEMENTS_SAVING_TIME = "$PREFS_PREFIX.entitlementsSavingTime"
    const val PUSH_TOKEN_KEY = "$PREFS_PREFIX.push_token_key"
    const val PENDING_PUSH_TOKEN_KEY = "$PREFS_PREFIX.pending_push_token_key"
    const val USER_ID_PREFIX = "QON"
    const val USER_ID_SEPARATOR = "_"
    const val EXPERIMENT_STARTED_EVENT_NAME = "offering_within_experiment_called"
    const val INTERNAL_SERVER_ERROR_MIN = 500
    const val INTERNAL_SERVER_ERROR_MAX = 599
    const val PRICE_MICROS_DIVIDER: Double = 1000000.0
}
