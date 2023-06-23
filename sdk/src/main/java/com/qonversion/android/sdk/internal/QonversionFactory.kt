package com.qonversion.android.sdk.internal

import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.qonversion.android.sdk.internal.billing.BillingService
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache

@SuppressWarnings("LongParameterList")
internal class QonversionFactory(
    private val context: Application,
    private val logger: Logger
) {
    fun createProductCenterManager(
        repository: QonversionRepository,
        purchasesCache: PurchasesCache,
        handledPurchasesCache: QHandledPurchasesCache,
        launchResultCacheWrapper: LaunchResultCacheWrapper,
        userInfoService: QUserInfoService,
        identityManager: QIdentityManager,
        config: InternalConfig,
        appStateProvider: AppStateProvider,
        remoteConfigManager: QRemoteConfigManager
    ): QProductCenterManager {
        val productCenterManager = QProductCenterManager(
            context,
            repository,
            logger,
            purchasesCache,
            handledPurchasesCache,
            launchResultCacheWrapper,
            userInfoService,
            identityManager,
            config,
            appStateProvider,
            remoteConfigManager
        )
        val billingService = createBillingService(productCenterManager)

        productCenterManager.billingService = billingService
        productCenterManager.consumer = createConsumer(billingService, config.isAnalyticsMode)

        return productCenterManager
    }

    private fun createBillingService(listener: QonversionBillingService.PurchasesListener): QonversionBillingService {
        val billingService = QonversionBillingService(
            Handler(context.mainLooper),
            listener,
            logger
        )
        billingService.billingClient = createBillingClient(billingService)

        return billingService
    }

    @UiThread
    private fun createBillingClient(listener: PurchasesUpdatedListener): BillingClient {
        val builder = BillingClient.newBuilder(context)
        builder.enablePendingPurchases()
        builder.setListener(listener)
        return builder.build()
    }

    private fun createConsumer(billingService: BillingService, isAnalyticsMode: Boolean): Consumer {
        return Consumer(billingService, isAnalyticsMode)
    }
}
