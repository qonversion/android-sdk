package com.qonversion.android.sdk.dto

import java.util.Date

data class User(
    val id: String,
    val entitlements: Set<Entitlement> = emptySet(),
    val purchases: Set<UserPurchase> = emptySet(),
    val created: Date?,
    val lastOnline: Date?
)
