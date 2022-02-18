package com.qonversion.android.sdk.internal.di.misc

import androidx.lifecycle.ProcessLifecycleOwner
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import com.qonversion.android.sdk.internal.user.generator.UserIdGenerator
import com.qonversion.android.sdk.internal.user.generator.UserIdGeneratorImpl
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorkerImpl
import java.util.Locale
import kotlin.random.Random

internal class MiscAssemblyImpl(
    private val internalConfig: InternalConfig
) : MiscAssembly {

    override fun logger(): Logger = ConsoleLogger(internalConfig)

    override fun locale(): Locale = Locale.getDefault()

    override fun jsonSerializer(): Serializer = JsonSerializer()

    override fun exponentialDelayCalculator(): RetryDelayCalculator =
        ExponentialDelayCalculator(Random)

    override fun appLifecycleObserver(): AppLifecycleObserver {
        val instance = AppLifecycleObserverImpl()
        ProcessLifecycleOwner.get().lifecycle.addObserver(instance)
        return instance
    }

    override fun delayedWorker(): DelayedWorker = DelayedWorkerImpl()

    override fun userIdGenerator(): UserIdGenerator = UserIdGeneratorImpl()
}
