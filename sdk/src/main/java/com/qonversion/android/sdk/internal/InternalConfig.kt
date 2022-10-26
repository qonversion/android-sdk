package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.internal.dto.config.StoreConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.provider.CacheConfigProvider

import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import com.qonversion.android.sdk.internal.provider.UidProvider

internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    val storeConfig: StoreConfig,
    override val cacheConfig: CacheConfig
) : EnvironmentProvider,
    PrimaryConfigProvider,
    CacheConfigProvider,
    UidProvider {

    @Volatile
    var fatalError: HttpError? = null
        @Synchronized set
        @Synchronized get

    @Volatile
    override var uid = ""
        @Synchronized set
        @Synchronized get

    constructor(qonversionConfig: QonversionConfig) : this(
        qonversionConfig.primaryConfig,
        qonversionConfig.storeConfig,
        qonversionConfig.cacheConfig
    )

    override val environment
        get() = primaryConfig.environment

    override val isSandbox get() = environment === Environment.Sandbox

    val isObserveMode get() = primaryConfig.launchMode == LaunchMode.ObserveMode
}
