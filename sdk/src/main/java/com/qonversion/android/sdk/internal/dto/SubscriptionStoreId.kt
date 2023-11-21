package com.qonversion.android.sdk.internal.dto

internal data class SubscriptionStoreId(
    val subscriptionId: String,
    val basePlanId: String?, // absent for inapp products
    val offerId: String? = null
)
