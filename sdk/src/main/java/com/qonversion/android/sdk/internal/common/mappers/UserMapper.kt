package com.qonversion.android.sdk.internal.common.mappers

import com.qonversion.android.sdk.dto.Entitlement
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.dto.UserPurchase

internal class UserMapper(
    private val purchasesMapper: Mapper<UserPurchase?>,
    private val entitlementsMapper: Mapper<Entitlement?>
) : Mapper<User?> {

    override fun fromMap(data: Map<*, *>): User? {
        val id = data.getString("id")

        if (id.isNullOrEmpty()) {
            return null
        }

        val createdDate = data.getDate("created")
        val lastOnlineDate = data.getDate("last_online")

        val entitlements = data.getList("entitlements")?.let {
            entitlementsMapper.fromList(it)
        } ?: emptyList()

        val purchases = data.getList("purchases")?.let {
            purchasesMapper.fromList(it)
        } ?: emptyList()

        return User(
            id,
            entitlements.filterNotNull(),
            purchases.filterNotNull(),
            createdDate,
            lastOnlineDate
        )
    }
}
