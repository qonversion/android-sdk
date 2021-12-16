package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo

internal interface GoogleBillingPurchaser {

    suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    )
}
