package com.qonversion.android.sdk.dto.entitlements

enum class QTransactionOwnershipType(val type: String) {
    Owner("owner"),
    FamilySharing("family_sharing");

    companion object {
        internal fun fromType(type: String): QTransactionOwnershipType {
            return when (type) {
                "owner" -> Owner
                "family_sharing" -> FamilySharing
                else -> Owner
            }
        }
    }
}
