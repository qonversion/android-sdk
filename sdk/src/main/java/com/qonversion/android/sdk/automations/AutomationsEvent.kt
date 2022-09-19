package com.qonversion.android.sdk.automations

import java.util.*

data class AutomationsEvent(
    val type: AutomationsEventType,
    val date: Date,
    private val productId: String? // Temporarily inaccessible
)
