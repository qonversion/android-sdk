package io.qonversion.nocodes.interfaces

import io.qonversion.nocodes.dto.QNoCodeScreen
import io.qonversion.nocodes.error.NoCodesError

/**
 * Callback for the result of [io.qonversion.nocodes.NoCodes.loadScreen].
 * Invoked on the main thread.
 *
 * This interface uses callback-based methods.
 * For Kotlin coroutines, use the suspend variant of [io.qonversion.nocodes.NoCodes.loadScreen].
 */
interface NoCodesScreenLoadCallback {

    /**
     * Invoked when the screen was successfully loaded and cached.
     *
     * @param screen the loaded screen.
     */
    fun onSuccess(screen: QNoCodeScreen)

    /**
     * Invoked when the screen failed to load.
     *
     * @param error the error occurred. [NoCodesError.code] is
     * [io.qonversion.nocodes.error.ErrorCode.ScreenNotFound] when no screen exists
     * for the provided context key; other codes indicate a transient load failure.
     */
    fun onError(error: NoCodesError)
}
