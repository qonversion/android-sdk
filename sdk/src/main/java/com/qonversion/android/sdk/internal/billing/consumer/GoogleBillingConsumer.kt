package com.qonversion.android.sdk.internal.billing.consumer

import com.qonversion.android.sdk.internal.billing.GoogleBillingHelper
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface GoogleBillingConsumer : GoogleBillingHelper {

    @Throws(QonversionException::class)
    fun consume(purchaseToken: String)

    @Throws(QonversionException::class)
    fun acknowledge(purchaseToken: String)
}
