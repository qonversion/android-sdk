package com.qonversion.android.sdk.automations.dto

import com.qonversion.android.sdk.automations.dto.AutomationsEventType
import java.util.*

data class AutomationsEvent(
    val type: AutomationsEventType,
    val date: Date,
    private val productId: String? // Temporarily inaccessible
)
