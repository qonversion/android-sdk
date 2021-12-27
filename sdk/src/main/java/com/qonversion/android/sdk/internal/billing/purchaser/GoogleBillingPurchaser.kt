package com.qonversion.android.sdk.internal.billing.purchaser

import android.app.Activity
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.billing.GoogleBillingHelper
import com.qonversion.android.sdk.internal.billing.dto.UpdatePurchaseInfo
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface GoogleBillingPurchaser : GoogleBillingHelper {

    @Throws(QonversionException::class)
    suspend fun purchase(
        activity: Activity,
        skuDetails: SkuDetails,
        updatePurchaseInfo: UpdatePurchaseInfo? = null
    )
}
