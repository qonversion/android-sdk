package com.qonversion.android.sdk.internal.billing.consumer

import com.qonversion.android.sdk.internal.billing.GoogleBillingHelper

internal interface GoogleBillingConsumer : GoogleBillingHelper {

    fun consume(purchaseToken: String)

    fun acknowledge(purchaseToken: String)
}
