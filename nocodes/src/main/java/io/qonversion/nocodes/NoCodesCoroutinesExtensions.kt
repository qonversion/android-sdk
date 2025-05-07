package io.qonversion.nocodes

import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.interfaces.NoCodesShowScreenCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Show the screen using its context key.
 * @param contextKey the context key of the screen which must be shown.
 */
@JvmSynthetic
@Throws(NoCodesException::class)
suspend fun NoCodes.showScreen(contextKey: String) {
    return suspendCoroutine { continuation ->
        showScreen(
            contextKey,
            object : NoCodesShowScreenCallback {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(error: NoCodesError) {
                    continuation.resumeWithException(NoCodesException(error))
                }
            }
        )
    }
}
