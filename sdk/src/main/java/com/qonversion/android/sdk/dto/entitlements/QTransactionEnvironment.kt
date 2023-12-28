package com.qonversion.android.sdk.dto.entitlements

enum class QTransactionEnvironment(val type: String) {
    Sandbox("sandbox"),
    Production("production");

    companion object {
        internal fun fromType(type: String): QTransactionEnvironment {
            return when (type) {
                "sandbox" -> Sandbox
                "production" -> Production
                else -> Production
            }
        }
    }
}