package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode

internal val Int.daysToSeconds get() = this * 24L * 60 * 60

internal val Int.daysToMs get() = daysToSeconds * 1000

internal val QonversionError.shouldFireFallback
    get(): Boolean =
        this.code == QonversionErrorCode.NetworkConnectionFailed ||
                // A locally short-circuited request (rate limiter) is exactly the
                // case the bundled payload exists for: no network attempt was
                // made, so serving the fallback beats surfacing a hard error.
                // Matters since fallbacks are no longer cached - offline apps
                // hit the limiter instead of the old cached-fallback fast path.
                this.code == QonversionErrorCode.ApiRateLimitExceeded ||
                this.httpCode?.isInternalServerError() == true
