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
    val renewState: QEntitlementRenewState
) {
    internal constructor(permission: QPermission) : this(
        permission.permissionID,
        permission.startedDate,
        permission.expirationDate,
        permission.isActive(),
        permission.source,
        permission.productID,
        QEntitlementRenewState.fromProductRenewState(permission.renewState)
    )

    val isActive get() = active
}
