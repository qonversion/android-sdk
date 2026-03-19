package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.internal.dto.config.PrimaryConfig
import com.qonversion.android.sdk.dto.QEnvironment
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.provider.CacheConfigProvider
import com.qonversion.android.sdk.internal.provider.EnvironmentProvider
import com.qonversion.android.sdk.internal.provider.PrimaryConfigProvider
import com.qonversion.android.sdk.internal.provider.UidProvider
import com.qonversion.android.sdk.listeners.QDeferredPurchasesListener

// Review feedback (Task 6): removed entitlementsUpdateListener property.
// InternalConfig now only holds deferredPurchasesListener. When the deprecated
// setEntitlementsUpdateListener() is called, the legacy listener is wrapped in
// EntitlementsUpdateListenerAdapter and stored as deferredPurchasesListener.
// This eliminates duplicate listener storage and dual invocation logic.
internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    override val cacheConfig: CacheConfig,
    var deferredPurchasesListener: QDeferredPurchasesListener? = null
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
        qonversionConfig.cacheConfig,
        qonversionConfig.deferredPurchasesListener
    )

    override val apiUrl: String
        get() = primaryConfig.proxyUrl ?: BASE_URL

    override val environment
        get() = primaryConfig.environment

    override val isSandbox get() = environment === QEnvironment.Sandbox

    val isAnalyticsMode get() = primaryConfig.launchMode == QLaunchMode.Analytics

    companion object {
        private const val BASE_URL = "https://api2.qonversion.io/"
    }
}
