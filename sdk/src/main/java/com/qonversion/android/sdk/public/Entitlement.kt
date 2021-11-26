package com.qonversion.android.sdk.public

import java.util.Date

data class Entitlement(
    val id: String,
    val userID: String,
    val active: Boolean,
    val startDate: Date,
    val expirationDate: Date,
    val purchases: List<UserPurchase> = emptyList()
)
