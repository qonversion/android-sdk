package io.qonversion.nocodes.internal.dto

import io.qonversion.nocodes.dto.QScreenVariable

internal data class NoCodeScreen(
    val id: String,
    val body: String,
    val contextKey: String,
    // Qonversion product ids configured in the builder for this screen (may be empty).
    val products: List<String> = emptyList(),
    // Screen variables authored in the builder for this screen, read by key (may be empty).
    val variables: List<QScreenVariable> = emptyList()
)
