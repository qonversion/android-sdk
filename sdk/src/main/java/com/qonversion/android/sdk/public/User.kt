package com.qonversion.android.sdk.public

import java.util.Date

data class User(
    val id: String,
    val entitlements: List<Entitlement> = emptyList(),
    val purchases: List<UserPurchase> = emptyList(),
    val created: Date?,
    val lastOnline: Date?
)
