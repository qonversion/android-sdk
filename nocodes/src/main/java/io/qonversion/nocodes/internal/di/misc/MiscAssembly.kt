package io.qonversion.nocodes.internal.di.misc

import io.qonversion.nocodes.internal.common.serializers.Serializer
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.screen.misc.ActivityProvider
import java.util.*

internal interface MiscAssembly {

    fun logger(): Logger

    fun locale(): Locale

    fun jsonSerializer(): Serializer

    fun exponentialDelayCalculator(): RetryDelayCalculator

    fun activityProvider(): ActivityProvider

    fun noCodesDelegateProvider(): NoCodesDelegateProvider
}