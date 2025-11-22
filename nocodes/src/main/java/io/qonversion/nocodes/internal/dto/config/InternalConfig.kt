package io.qonversion.nocodes.internal.dto.config

import io.qonversion.nocodes.NoCodesConfig
import io.qonversion.nocodes.dto.LogLevel
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.PurchaseHandlerDelegate
import io.qonversion.nocodes.interfaces.ScreenCustomizationDelegate
import io.qonversion.nocodes.internal.provider.LoggerConfigProvider
import io.qonversion.nocodes.internal.provider.NetworkConfigHolder
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.provider.PrimaryConfigProvider
import io.qonversion.nocodes.internal.provider.PurchaseHandlerDelegateProvider
import java.lang.ref.WeakReference

internal class InternalConfig(
    override var primaryConfig: PrimaryConfig,
    val networkConfig: NetworkConfig,
    var loggerConfig: LoggerConfig,
    override var noCodesDelegate: NoCodesDelegate?,
    var screenCustomizationDelegate: WeakReference<ScreenCustomizationDelegate>?,
    var purchaseHandlerDelegateRef: WeakReference<PurchaseHandlerDelegate>?,
) : PrimaryConfigProvider,
    LoggerConfigProvider,
    NetworkConfigHolder,
    NoCodesDelegateProvider,
    PurchaseHandlerDelegateProvider {

    constructor(noCodesConfig: NoCodesConfig) : this(
        noCodesConfig.primaryConfig,
        noCodesConfig.networkConfig,
        noCodesConfig.loggerConfig,
        noCodesConfig.noCodesDelegate?.let { NoCodesDelegateWrapper(it) },
        noCodesConfig.screenCustomizationDelegate?.let { WeakReference(it) },
        noCodesConfig.purchaseHandlerDelegate?.let { WeakReference(it) }
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

    override var purchaseHandlerDelegate: PurchaseHandlerDelegate?
        get() = purchaseHandlerDelegateRef?.get()
        set(value) {
            purchaseHandlerDelegateRef = value?.let { WeakReference(it) }
        }
}
