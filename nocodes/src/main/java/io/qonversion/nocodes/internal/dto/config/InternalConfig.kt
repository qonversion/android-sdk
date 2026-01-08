package io.qonversion.nocodes.internal.dto.config

import io.qonversion.nocodes.NoCodesConfig
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.PurchaseDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.provider.LocaleConfigProvider
import io.qonversion.nocodes.internal.provider.LoggerConfigProvider
import io.qonversion.nocodes.internal.provider.NetworkConfigHolder
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.provider.PrimaryConfigProvider
import io.qonversion.nocodes.internal.provider.PurchaseDelegateProvider

internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    val networkConfig: NetworkConfig,
    var loggerConfig: LoggerConfig,
    override var noCodesDelegate: NoCodesDelegate?,
    var screenCustomizationDelegate: ScreenCustomizationDelegate?,
    override var purchaseDelegate: PurchaseDelegate?,
    override var customLocale: String? = null,
) : PrimaryConfigProvider,
    LoggerConfigProvider,
    NetworkConfigHolder,
    NoCodesDelegateProvider,
    PurchaseDelegateProvider,
    LocaleConfigProvider {

    constructor(noCodesConfig: NoCodesConfig) : this(
        noCodesConfig.primaryConfig,
        noCodesConfig.networkConfig,
        noCodesConfig.loggerConfig,
        noCodesConfig.noCodesDelegate?.let { NoCodesDelegateWrapper(it) },
        noCodesConfig.screenCustomizationDelegate,
        // If PurchaseDelegate is provided, use it directly. Otherwise, wrap PurchaseDelegateWithCallbacks.
        noCodesConfig.purchaseDelegate
            ?: noCodesConfig.purchaseDelegateWithCallbacks?.let { PurchaseDelegateWithCallbacksAdapter(it) },
        noCodesConfig.locale
    )

    override var canSendRequests: Boolean
        get() = networkConfig.canSendRequests
        set(value) {
            networkConfig.canSendRequests = value
        }

    override val logLevel: LogLevel
        get() = loggerConfig.logLevel

    override val logTag: String
        get() = loggerConfig.logTag
}
