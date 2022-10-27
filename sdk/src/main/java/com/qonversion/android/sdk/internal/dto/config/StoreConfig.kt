package com.qonversion.android.sdk.internal.dto.config

import com.qonversion.android.sdk.dto.Store

internal data class StoreConfig(
    val store: Store,
    val shouldConsumePurchases: Boolean
)
