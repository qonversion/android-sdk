package com.qonversion.android.sdk.internal

import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.qonversion.android.sdk.internal.billing.BillingClientWrapper
import com.qonversion.android.sdk.internal.billing.BillingClientHolder
import com.qonversion.android.sdk.internal.billing.QonversionBillingService
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.AppStateProvider
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.services.QUserInfoService
import com.qonversion.android.sdk.internal.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.internal.storage.PurchasesCache

@SuppressWarnings("LongParameterList")
internal class QonversionFactory(
    private val context: Application,
    private val logger: Logger
) {
    fun createProductCenterManager(
        repository: QRepository,
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
        val billingService = createBillingService(productCenterManager, config.isAnalyticsMode)

        productCenterManager.billingService = billingService

        return productCenterManager
    }

    private fun createBillingService(
        listener: QonversionBillingService.PurchasesListener,
        isAnalyticsMode: Boolean
    ): QonversionBillingService {
        val billingClientHolder = createBillingClientHolder()
        return QonversionBillingService(
            Handler(context.mainLooper),
            listener,
            logger,
            isAnalyticsMode,
            billingClientHolder,
            createBillingClientWrapper(billingClientHolder)
        )
    }

    private fun createBillingClientHolder(): BillingClientHolder {
        val clientHolder = BillingClientHolder(
            Handler(context.mainLooper),
            logger
        )

        val billingClient = createBillingClient(clientHolder)
        clientHolder.setBillingClient(billingClient)

        return clientHolder
    }

    private fun createBillingClientWrapper(
        billingClientHolder: BillingClientHolder
    ): BillingClientWrapper {
        return BillingClientWrapper(billingClientHolder, logger)
    }

    @UiThread
    private fun createBillingClient(listener: PurchasesUpdatedListener): BillingClient {
        val builder = BillingClient.newBuilder(context)
        builder.enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        builder.setListener(listener)
        return builder.build()
    }
}
