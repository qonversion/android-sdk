package com.qonversion.android.sdk.internal.billing.consumer

internal interface GoogleBillingConsumer {

    fun consume(purchaseToken: String)

    fun acknowledge(purchaseToken: String)
}
