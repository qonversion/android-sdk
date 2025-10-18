package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.dto.NoCodeScreen

internal interface ScreenService {

    @Throws(NoCodesException::class)
    suspend fun getScreen(contextKey: String): NoCodeScreen

    @Throws(NoCodesException::class)
    suspend fun getScreenById(screenId: String): NoCodeScreen

    suspend fun preloadScreens(): List<NoCodeScreen>
}
