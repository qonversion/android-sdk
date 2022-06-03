package com.qonversion.android.sdk

fun Int.toBoolean() = this != 0

fun String?.toBoolean() = this == "1"

fun Boolean.toInt() = if (this) 1 else 0

fun Boolean.stringValue() = if (this) "1" else "0"
