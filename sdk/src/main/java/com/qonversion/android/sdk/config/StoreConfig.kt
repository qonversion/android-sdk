package com.qonversion.android.sdk.config

import com.qonversion.android.sdk.dto.Store

data class StoreConfig(
    val store: Store,
    val shouldConsumePurchases: Boolean
)
