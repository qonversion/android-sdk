package io.qonversion.nocodes.internal.screen.controller

internal interface ScreenController {
    suspend fun showScreen(contextKey: String)

    fun close()
}
