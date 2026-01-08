package io.qonversion.nocodes.internal.di.misc

import io.qonversion.nocodes.internal.common.serializers.Serializer
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.provider.PurchaseDelegateProvider
import io.qonversion.nocodes.internal.screen.misc.ActivityProvider
import java.util.Locale

internal interface MiscAssembly {

    fun logger(): Logger

    fun locale(): Locale

    /**
     * Returns the custom locale string set by the client, or null if using system default.
     */
    fun customLocale(): String?

    fun jsonSerializer(): Serializer

    fun exponentialDelayCalculator(): RetryDelayCalculator

    fun activityProvider(): ActivityProvider

    fun noCodesDelegateProvider(): NoCodesDelegateProvider

    fun purchaseDelegateProvider(): PurchaseDelegateProvider
}
