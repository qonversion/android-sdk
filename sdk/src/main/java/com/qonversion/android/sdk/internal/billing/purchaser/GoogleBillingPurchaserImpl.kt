package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.billing.utils.getDescription
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GoogleBillingPurchaserImpl(
    private val billingClient: BillingClient,
    logger: Logger
) : BaseClass(logger), GoogleBillingPurchaser {

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

    private fun BillingFlowParams.Builder.setSubscriptionUpdateParams(
        info: UpdatePurchaseInfo? = null
    ): BillingFlowParams.Builder {
        if (info != null) {
            val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldSkuPurchaseToken(info.purchaseToken)
                .apply {
                    info.prorationMode?.let {
                        setReplaceSkusProrationMode(it)
                    }
                }
                .build()

            setSubscriptionUpdateParams(updateParams)
        }

        return this
    }
}
