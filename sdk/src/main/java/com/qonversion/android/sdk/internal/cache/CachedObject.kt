package com.qonversion.android.sdk.internal.cache

import java.util.Date

internal data class CachedObject<T>(val date: Date, val value: T?)
