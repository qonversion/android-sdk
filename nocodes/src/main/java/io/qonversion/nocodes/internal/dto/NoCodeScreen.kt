package io.qonversion.nocodes.internal.dto

internal data class NoCodeScreen(
    val id: String,
    val body: String,
    val contextKey: String,
    // Qonversion product ids configured in the builder for this screen (may be empty).
    val products: List<String> = emptyList()
)
