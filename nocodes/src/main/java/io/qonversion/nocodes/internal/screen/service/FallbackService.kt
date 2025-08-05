package io.qonversion.nocodes.internal.screen.service

import android.content.Context
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal interface FallbackService {
    suspend fun loadScreen(contextKey: String): NoCodeScreen?
    suspend fun loadScreenById(screenId: String): NoCodeScreen?
}
