package io.qonversion.android.sdk.dto

import io.qonversion.android.sdk.Qonversion

/**
 * This enum contains different available settings for Environment.
 * Provide it to the configuration object via [Qonversion.initialize] while initializing the SDK.
 * The default value SDK uses is [QEnvironment.Production].
 */
enum class QEnvironment {
    Sandbox,
    Production
}
