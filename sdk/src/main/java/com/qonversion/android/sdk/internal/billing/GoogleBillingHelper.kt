package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient

internal interface GoogleBillingHelper {

    fun setup(billingClient: BillingClient)
}
