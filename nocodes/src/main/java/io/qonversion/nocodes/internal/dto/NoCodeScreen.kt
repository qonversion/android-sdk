package io.qonversion.nocodes.internal.dto

import io.qonversion.nocodes.dto.QScreenVariable

internal data class NoCodeScreen(
    val id: String,
    val body: String,
    val contextKey: String,
    // Typed default variables configured in the builder for this screen (may be empty).
    val variables: List<QScreenVariable> = emptyList()
)
