package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import androidx.annotation.UiThread
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.qonversion.android.sdk.billing.BillingService
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.logger.Logger
import com.qonversion.android.sdk.storage.PurchasesCache
import com.qonversion.android.sdk.storage.LaunchResultCache

class QonversionFactory internal constructor(
    private val context: Application,
    private val logger: Logger
) {
    fun createProductCenterManager(
        repository: QonversionRepository,
        isObserveMode: Boolean,
        purchasesCache: PurchasesCache,
        launchResultCache: LaunchResultCache
    ): QProductCenterManager {
        val productCenterManager = QProductCenterManager(context, repository, logger, purchasesCache, launchResultCache)
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