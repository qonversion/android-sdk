package com.qonversion.android.sdk.dto.entitlements

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class QTransaction(
    @Json(name = "original_transaction_id") val originalTransactionId: String,
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "offer_code") val offerCode: String,
    @Json(name = "transaction_timestamp") val transactionDate: Date,
    @Json(name = "expiration_timestamp") val expirationDate: Date?,
    @Json(name = "transaction_revoke_timestamp") val transactionRevocationDate: Date?,
    @Json(name = "ownership_type") val ownershipType: QTransactionOwnershipType,
    @Json(name = "type") val type: QTransactionType,
    @Json(name = "environment") val environment: QTransactionEnvironment,
)
