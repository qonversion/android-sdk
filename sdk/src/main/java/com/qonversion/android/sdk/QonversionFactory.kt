package com.qonversion.android.sdk

import android.app.Application
import android.os.Handler
import com.qonversion.android.sdk.billing.BillingService
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.logger.Logger

class QonversionFactory internal constructor(
    private val context: Application,
    private val logger: Logger
) {
    fun createBillingService(listener: QonversionBillingService.PurchasesListener): BillingService {
        return QonversionBillingService(
            QonversionBillingService.BillingBuilder(context),
            Handler(context.mainLooper),
            listener,
            logger
        )
    }
}