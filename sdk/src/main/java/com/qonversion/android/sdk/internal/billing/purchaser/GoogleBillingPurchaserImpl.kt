package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.billing.utils.setSubscriptionUpdateParams
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GoogleBillingPurchaserImpl(logger: Logger) : BaseClass(logger), GoogleBillingPurchaser {

    private lateinit var billingClient: BillingClient

    override fun setup(billingClient: BillingClient) {
        this.billingClient = billingClient
    }

    override suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        updatePurchaseInfo: UpdatePurchaseInfo?
    ) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .setSubscriptionUpdateParams(updatePurchaseInfo)
            .build()

        withContext(Dispatchers.Main) {
            val billingResult = billingClient.launchBillingFlow(activity, params)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw QonversionException(
                    ErrorCode.Purchasing,
                    "Failed to launch billing flow. ${billingResult.getDescription()}"
                )
            }
        }
    }
}
