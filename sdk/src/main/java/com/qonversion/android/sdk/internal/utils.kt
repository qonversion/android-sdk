package com.qonversion.android.sdk.internal

internal val Int.daysToSeconds get() = this * 24L * 60 * 60

internal val Int.daysToMs get() = daysToSeconds * 1000
