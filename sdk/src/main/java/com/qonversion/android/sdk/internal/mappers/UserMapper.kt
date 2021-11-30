package com.qonversion.android.sdk.internal.mappers

import com.qonversion.android.sdk.internal.extensions.mapping.getString
import com.qonversion.android.sdk.internal.extensions.mapping.getDate
import com.qonversion.android.sdk.internal.extensions.mapping.getMap
import com.qonversion.android.sdk.public.Entitlement
import com.qonversion.android.sdk.public.User
import com.qonversion.android.sdk.public.UserPurchase

class UserMapper internal constructor(
    private val purchasesMapper: Mapper<UserPurchase>,
    private val entitlementMapper: Mapper<Entitlement>
) : Mapper<User> {

    override fun fromMap(data: Map<String, Any?>): User? {
        val id = data.getString("id")

        if (id.isNullOrEmpty()) {
            return null
        }

        val createdDate = data.getDate("created")
        val lastOnlineDate = data.getDate("last_online")

        val entitlementsData = data.getMap("entitlements")
        var entitlements: List<Entitlement> = emptyList()

        val purchasesData = data.getMap("purchases")
        var purchases: List<UserPurchase> = emptyList()

        if (entitlementsData != null) {
            entitlements = entitlementMapper.arrayFromMap(entitlementsData)
        }

        if (purchasesData != null) {
            purchases = purchasesMapper.arrayFromMap(purchasesData)
        }

        return User(id, entitlements, purchases, createdDate, lastOnlineDate)
    }
}
