package io.qonversion.nocodes.internal.di.misc

import android.app.Application
import io.qonversion.nocodes.internal.common.serializers.JsonSerializer
import io.qonversion.nocodes.internal.common.serializers.Serializer
import io.qonversion.nocodes.internal.dto.config.InternalConfig
import io.qonversion.nocodes.internal.logger.ConsoleLogger
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import io.qonversion.nocodes.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import io.qonversion.nocodes.internal.provider.NoCodesDelegateProvider
import io.qonversion.nocodes.internal.screen.misc.ActivityProvider
import kotlin.random.Random
import java.util.*

internal class MiscAssemblyImpl(
    private val application: Application,
    private val internalConfig: InternalConfig
) : MiscAssembly {

    private val activityProvider: ActivityProvider by lazy {
        ActivityProvider(application)
    }

    override fun logger(): Logger = ConsoleLogger(internalConfig)

    override fun locale(): Locale = Locale.getDefault()

    override fun jsonSerializer(): Serializer = JsonSerializer()

    override fun exponentialDelayCalculator(): RetryDelayCalculator =
        ExponentialDelayCalculator(Random)

    override fun activityProvider(): ActivityProvider = activityProvider

    override fun noCodesDelegateProvider(): NoCodesDelegateProvider = internalConfig
}