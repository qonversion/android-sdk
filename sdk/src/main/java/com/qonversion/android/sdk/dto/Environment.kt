package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.Qonversion

/**
 * This enum contains different available settings for Environment.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK.
 * The default value SDK uses is [Environment.Production].
 */
enum class Environment {
    Sandbox,
    Production
}
