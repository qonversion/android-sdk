package com.qonversion.android.sdk.public

import java.util.Date

data class UserPurchase(
    val userID: String,
    val originalTransactionID: String,
    val purchaseToken: String,
    val platform: String,
    val platformProductID: String,
    val product: String,
    val currency: String,
    val amount: Int,
    val date: Date,
    val createdDate: Date,
    val deviceID: String
)
