package com.qonversion.android.sdk

internal fun Int.toBoolean() = this != 0

internal fun String?.toBoolean() = this == "1"

internal fun Boolean.toInt() = if (this) 1 else 0

internal fun Boolean.stringValue() = if (this) "1" else "0"

internal val Int.daysToSeconds get() = this * 24L * 60 * 60

internal val Int.daysToMs get() = daysToSeconds * 1000
