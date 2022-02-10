package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserverImpl
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumerImpl
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcherImpl
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaserImpl
import com.qonversion.android.sdk.internal.common.PREFS_NAME
import com.qonversion.android.sdk.internal.common.serializers.JsonSerializer
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.ConsoleLogger
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.ExponentialDelayCalculator
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorkerImpl
import java.util.Locale
import kotlin.random.Random

internal class MiscAssemblyImpl(
    override val application: Application
) : MiscAssembly {
    override val internalConfig: InternalConfig
        get() = InternalConfig

    override val logger: Logger
        get() = ConsoleLogger(internalConfig)

    override val sharedPreferences: SharedPreferences
        get() = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val locale: Locale
        get() = Locale.getDefault()

    override val jsonSerializer: Serializer
        get() = JsonSerializer()

    override val exponentialDelayCalculator: RetryDelayCalculator
        get() = ExponentialDelayCalculator(Random)

    override val appLifecycleObserver: AppLifecycleObserver
        get() {
            val instance = AppLifecycleObserverImpl()
            application.registerActivityLifecycleCallbacks(instance)
            return instance
        }

    override val delayedWorker: DelayedWorker
        get() = DelayedWorkerImpl()

    override val googleBillingConsumer: GoogleBillingConsumer
        get() = GoogleBillingConsumerImpl(logger)

    override val googleBillingPurchaser: GoogleBillingPurchaser
        get() = GoogleBillingPurchaserImpl(logger)

    override val googleBillingDataFetcher: GoogleBillingDataFetcher
        get() = GoogleBillingDataFetcherImpl(logger)
}
