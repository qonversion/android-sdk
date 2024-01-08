package io.qonversion.android.sdk.internal

import io.qonversion.android.sdk.QonversionConfig
import io.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import io.qonversion.android.sdk.dto.QEnvironment
import io.qonversion.android.sdk.dto.QLaunchMode
import io.qonversion.android.sdk.internal.dto.config.CacheConfig
import io.qonversion.android.sdk.internal.provider.CacheConfigProvider
import io.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider

import io.qonversion.android.sdk.internal.provider.EnvironmentProvider
import io.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import io.qonversion.android.sdk.internal.provider.UidProvider
import io.qonversion.android.sdk.listeners.QEntitlementsUpdateListener

internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    override val cacheConfig: CacheConfig,
    override var entitlementsUpdateListener: QEntitlementsUpdateListener?
) : EnvironmentProvider,
    PrimaryConfigProvider,
    CacheConfigProvider,
    UidProvider,
    EntitlementsUpdateListenerProvider {

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
        qonversionConfig.cacheConfig,
        qonversionConfig.entitlementsUpdateListener
    )

    override val apiUrl: String
        get() = primaryConfig.proxyUrl ?: BASE_URL

    override val environment
        get() = primaryConfig.environment

    override val isSandbox get() = environment === QEnvironment.Sandbox

    val isAnalyticsMode get() = primaryConfig.launchMode == QLaunchMode.Analytics

    companion object {
        private const val BASE_URL = "https://api.qonversion.io/"
    }
}
