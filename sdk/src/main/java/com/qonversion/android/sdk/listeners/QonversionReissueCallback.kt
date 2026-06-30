package com.qonversion.android.sdk.listeners

import com.qonversion.android.sdk.dto.redemption.ReissueResult

/**
 * Callback used by [com.qonversion.android.sdk.Qonversion.reissueRedemption].
 *
 * Called once with the terminal [ReissueResult] for the reissue request.
 * All invocations are delivered on the main thread.
 */
interface QonversionReissueCallback {
    fun onResult(result: ReissueResult)
}
