package com.qonversion.android.sdk.old

import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.qonversion.android.sdk.old.billing.BillingService
import com.qonversion.android.sdk.old.billing.QonversionBillingService
import com.qonversion.android.sdk.old.logger.Logger
import com.qonversion.android.sdk.old.services.QUserInfoService
import com.qonversion.android.sdk.old.storage.LaunchResultCacheWrapper
import com.qonversion.android.sdk.old.storage.PurchasesCache

@SuppressWarnings("LongParameterList")
class QonversionFactory internal constructor(
    private val context: Application,
    private val logger: Logger
) {
    fun createProductCenterManager(
        repository: QonversionRepository,
        isObserveMode: Boolean,
        purchasesCache: PurchasesCache,
        handledPurchasesCache: QHandledPurchasesCache,
        launchResultCacheWrapper: LaunchResultCacheWrapper,
        userInfoService: QUserInfoService,
        identityManager: QIdentityManager,
        config: QonversionConfig
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
            config
        )
        val billingService = createBillingService(productCenterManager)

        productCenterManager.billingService = billingService
        productCenterManager.consumer = createConsumer(billingService, isObserveMode)

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

    private fun createConsumer(billingService: BillingService, isObserveMode: Boolean): Consumer {
        return Consumer(billingService, isObserveMode)
    }
}