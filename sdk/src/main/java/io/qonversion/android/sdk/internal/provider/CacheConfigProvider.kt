package io.qonversion.android.sdk.internal.provider

import io.qonversion.android.sdk.internal.dto.config.CacheConfig

internal interface CacheConfigProvider {

    val cacheConfig: CacheConfig
}
