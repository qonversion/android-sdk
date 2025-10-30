package com.qonversion.android.sdk.dto

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.entitlements.QEntitlement

enum class EntitlementsSource {
    Api,
    Local
}

/**
 * Represents the result of a purchase operation.
 * Contains all relevant information about the purchase outcome including entitlements,
 * errors, purchase details, and user cancellation status.
 *
 * @param entitlements Map of entitlements obtained as a result of the purchase
 * @param error Error that occurred during the purchase, if any
 * @param purchase Google Play Billing Purchase object associated with the purchase
 * @param isUserCanceled Flag indicating whether the user canceled the purchase
 * @param entitlementsSource Source of entitlements: Api or Local (fallback)
 */
data class QPurchaseResult(
    val entitlements: Map<String, QEntitlement>,
    val error: QonversionError?,
    val purchase: Purchase?,
    val isUserCanceled: Boolean,
    val entitlementsSource: EntitlementsSource
) {

    /**
     * Constructor for successful purchase (API entitlements)
     */
    constructor(
        entitlements: Map<String, QEntitlement>,
        purchase: Purchase
    ) : this(entitlements, null, purchase, false, EntitlementsSource.Api)

    /**
     * Constructor for error without purchase (no entitlements)
     */
    constructor(
        error: QonversionError,
        isUserCanceled: Boolean
    ) : this(emptyMap(), error, null, isUserCanceled, EntitlementsSource.Api)

    /**
     * Constructor for error with purchase
     */
    constructor(
        error: QonversionError,
        purchase: Purchase,
        isUserCanceled: Boolean
    ) : this(emptyMap(), error, purchase, isUserCanceled, EntitlementsSource.Api)

    /**
     * Indicates whether the purchase was successful (API entitlements and not canceled, no error)
     */
    val isSuccess: Boolean
        get() = error == null && !isUserCanceled && entitlements.isNotEmpty() && entitlementsSource == EntitlementsSource.Api

    /**
     * Indicates whether the entitlements were generated locally (fallback)
     */
    val isFallbackGenerated: Boolean
        get() = entitlements.isNotEmpty() && entitlementsSource == EntitlementsSource.Local

    /**
     * Indicates whether the purchase failed due to user cancellation
     */
    val isCanceled: Boolean
        get() = isUserCanceled

    /**
     * Indicates whether the purchase failed due to an error
     */
    val isError: Boolean
        get() = error != null
}
