package com.qonversion.android.sdk.dto

data class Product(
    val id: String,
    val type: ProductType,
    val currency: String,
    val price: Int,
    val introductoryPrice: Int,
    val introductoryPaymentMode: PaymentMode,
    val introductoryDuration: ProductDuration,
    val subscription: Subscription
)
