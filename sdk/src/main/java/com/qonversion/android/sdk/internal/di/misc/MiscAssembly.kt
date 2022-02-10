package com.qonversion.android.sdk.internal.di.misc

import android.app.Application
import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.billing.consumer.GoogleBillingConsumer
import com.qonversion.android.sdk.internal.billing.dataFetcher.GoogleBillingDataFetcher
import com.qonversion.android.sdk.internal.billing.purchaser.GoogleBillingPurchaser
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.networkLayer.retryDelayCalculator.RetryDelayCalculator
import com.qonversion.android.sdk.internal.utils.workers.DelayedWorker
import java.util.Locale

internal interface MiscAssembly {

    val application: Application

    val internalConfig: InternalConfig

    val logger: Logger

    val sharedPreferences: SharedPreferences

    val locale: Locale

    val jsonSerializer: Serializer

    val exponentialDelayCalculator: RetryDelayCalculator

    val appLifecycleObserver: AppLifecycleObserver

    val delayedWorker: DelayedWorker

    val googleBillingConsumer: GoogleBillingConsumer

    val googleBillingPurchaser: GoogleBillingPurchaser

    val googleBillingDataFetcher: GoogleBillingDataFetcher
}
