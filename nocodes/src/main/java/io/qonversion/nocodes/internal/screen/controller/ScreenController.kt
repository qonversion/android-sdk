package io.qonversion.nocodes.internal.screen.controller

import io.qonversion.nocodes.error.NoCodesException
import kotlin.jvm.Throws

internal interface ScreenController {
    @Throws(NoCodesException::class)
    suspend fun showScreen(contextKey: String)

    fun close()
}
