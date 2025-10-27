package com.qonversion.android.sdk.dto

import com.android.billingclient.api.Purchase
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.QonversionError

/**
 * Represents the result of a purchase operation.
 * Contains all relevant information about the purchase outcome including entitlements,
 * errors, purchase details, and user cancellation status.
 * 
 * @param entitlements Map of entitlements obtained as a result of the purchase
 * @param error Error that occurred during the purchase, if any
 * @param purchase Google Play Billing Purchase object associated with the purchase
 * @param isUserCanceled Flag indicating whether the user canceled the purchase
 */
data class QPurchaseResult(
    val entitlements: Map<String, QEntitlement>,
    val error: QonversionError?,
    val purchase: Purchase?,
    val isUserCanceled: Boolean
) {
    
    /**
     * Constructor for successful purchase
     * @param entitlements Map of entitlements obtained from the purchase
     * @param purchase Google Play Billing Purchase object
     */
    constructor(
        entitlements: Map<String, QEntitlement>,
        purchase: Purchase
    ) : this(entitlements, null, purchase, false)
    
    /**
     * Constructor for error without purchase
     * @param error Error that occurred during the purchase
     * @param isUserCanceled Whether the user canceled the purchase
     */
    constructor(
        error: QonversionError,
        isUserCanceled: Boolean
    ) : this(emptyMap(), error, null, isUserCanceled)
    
    /**
     * Constructor for error with purchase
     * @param error Error that occurred during the purchase
     * @param purchase Google Play Billing Purchase object
     * @param isUserCanceled Whether the user canceled the purchase
     */
    constructor(
        error: QonversionError,
        purchase: Purchase,
        isUserCanceled: Boolean
    ) : this(emptyMap(), error, purchase, isUserCanceled)
    
    /**
     * Indicates whether the purchase was successful
     */
    val isSuccess: Boolean
        get() = error == null && !isUserCanceled && entitlements.isNotEmpty()
    
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

