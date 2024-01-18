package com.qonversion.android.sdk.dto.entitlements

import com.qonversion.android.sdk.internal.dto.QPermission
import java.util.Date

data class QEntitlement(
    val id: String,
    val startedDate: Date,
    val expirationDate: Date?,
    internal val active: Boolean,
    val source: QEntitlementSource,
    val productId: String,
    val renewState: QEntitlementRenewState,
    val renewsCount: Int,
    val trialStartDate: Date?,
    val firstPurchaseDate: Date?,
    val lastPurchaseDate: Date?,
    val lastActivatedOfferCode: String?,
    val grantType: QEntitlementGrantType,
    val autoRenewDisableDate: Date?,
    val transactions: List<QTransaction>
) {
    internal constructor(permission: QPermission) : this(
        permission.permissionID,
        permission.startedDate,
        permission.expirationDate,
        permission.isActive(),
        permission.source,
        permission.productID,
        QEntitlementRenewState.fromProductRenewState(permission.renewState),
        permission.renewsCount,
        permission.trialStartDate,
        permission.firstPurchaseDate,
        permission.lastPurchaseDate,
        permission.lastActivatedOfferCode,
        permission.grantType,
        permission.autoRenewDisableDate,
        permission.transactions
    )

    val isActive get() = active
}
