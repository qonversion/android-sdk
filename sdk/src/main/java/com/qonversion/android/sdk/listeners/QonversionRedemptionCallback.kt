package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.redemption.RedemptionResult

/**
 * Callback used by [com.qonversion.android.sdk.Qonversion.handleRedemptionLink].
 *
 * Called once with the terminal [RedemptionResult] for the redemption attempt.
 * All invocations are delivered on the main thread.
 */
interface QonversionRedemptionCallback {
    fun onResult(result: RedemptionResult)
}
