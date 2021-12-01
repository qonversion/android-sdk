package com.qonversion.android.sdk.dto

import java.util.Date

data class Entitlement(
    val id: String,
    val userID: String,
    val active: Boolean,
    val startedDate: Date,
    val expirationDate: Date,
    val purchases: List<UserPurchase> = emptyList()
)
