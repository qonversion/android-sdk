package com.qonversion.android.sdk.automations.dto

import java.util.Date

data class AutomationsEvent(
    val type: AutomationsEventType,
    val date: Date,
    private val productId: String? // Temporarily inaccessible
)
