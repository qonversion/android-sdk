package com.qonversion.android.sdk.internal.cache

import com.qonversion.android.sdk.internal.utils.SEC_IN_MIN

enum class InternalCacheLifetime(val seconds: Long) {

    FIVE_MIN(5 * SEC_IN_MIN)
}
