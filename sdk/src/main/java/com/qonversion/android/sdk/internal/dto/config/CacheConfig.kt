package com.qonversion.android.sdk.internal.dto.config

import androidx.annotation.RawRes
import com.qonversion.android.sdk.dto.entitlements.QEntitlementsCacheLifetime

internal data class CacheConfig(
    val entitlementsCacheLifetime: QEntitlementsCacheLifetime,
    @RawRes val fallbackFileIdentifier: Int?
)
