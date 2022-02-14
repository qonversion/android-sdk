package com.qonversion.android.sdk.internal.di.misc

import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import java.util.Locale

internal interface MiscAssembly {

    fun logger(): Logger

    fun locale(): Locale

    fun jsonSerializer(): Serializer

    fun exponentialDelayCalculator(): RetryDelayCalculator

    fun appLifecycleObserver(): AppLifecycleObserver

    fun delayedWorker(): DelayedWorker
}
