package com.qonversion.android.sdk

object Constants {
    const val PREFS_PREFIX = "com.qonversion.keys"
    const val PREFS_ORIGINAL_USER_ID_KEY = "$PREFS_PREFIX.originalUserID"
    const val PREFS_QONVERSION_USER_ID_KEY = "$PREFS_PREFIX.storedUserID"
    const val PREFS_PARTNER_IDENTITY_ID_KEY = "$PREFS_PREFIX.partnerIdentityUserID"
    const val PUSH_TOKEN_KEY = "$PREFS_PREFIX.push_token_key"
    const val PENDING_PUSH_TOKEN_KEY = "$PREFS_PREFIX.pending_push_token_key"
    const val USER_ID_PREFIX = "QON"
    const val USER_ID_SEPARATOR = "_"
    const val EXPERIMENT_STARTED_EVENT_NAME = "offering_within_experiment_called"
    const val INTERNAL_SERVER_ERROR_MIN = 500
    const val INTERNAL_SERVER_ERROR_MAX = 599
    const val PRICE_MICROS_DIVIDER: Double = 1000000.0
    val FATAL_HTTP_ERRORS = listOf(401, 402, 403)
}
