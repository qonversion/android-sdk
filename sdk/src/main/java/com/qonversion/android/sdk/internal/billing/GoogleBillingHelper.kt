package com.qonversion.android.sdk.internal.billing

import com.android.billingclient.api.BillingClient

interface GoogleBillingHelper {

    fun setup(billingClient: BillingClient)
}
