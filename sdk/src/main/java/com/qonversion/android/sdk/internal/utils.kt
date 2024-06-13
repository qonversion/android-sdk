package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QonversionErrorCode

internal val Int.daysToSeconds get() = this * 24L * 60 * 60

internal val Int.daysToMs get() = daysToSeconds * 1000

internal val QonversionError.shouldFireFallback get(): Boolean = this.code == QonversionErrorCode.NetworkConnectionFailed || this.code.