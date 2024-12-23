package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal interface ScreenService {

    @Throws(NoCodesException::class)
    suspend fun getScreen(screenId: String): NoCodeScreen
}
