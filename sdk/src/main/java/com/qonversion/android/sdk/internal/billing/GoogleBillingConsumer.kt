package com.qonversion.android.sdk.internal.billing

internal interface GoogleBillingConsumer {

    fun consume(purchaseToken: String)

    fun acknowledge(purchaseToken: String)
}
