package com.qonversion.android.sdk.dto

import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.qonversion.android.sdk.Qonversion

/**
 * A policy used for purchase updates, which describes
 * how to migrate from purchased plan to a new one.
 *
 * Used in [QPurchaseUpdatePolicy] class for purchase updates.
 */
enum class QPurchaseUpdatePolicy {
    /**
     * The new plan takes effect immediately, and the user is charged full price of new plan
     * and is given a full billing cycle of subscription, plus remaining prorated time
     * from the old plan.
     */
    ChargeFullPrice,

    /**
     * The new plan takes effect immediately, and the billing cycle remains the same.
     */
    ChargeProratedPrice,

    /**
     * The new plan takes effect immediately, and the remaining time will be prorated
     * and credited to the user.
     */
    WithTimeProration,

    /**
     * The new purchase takes effect immediately, the new plan will take effect
     * when the old item expires.
     */
    Deferred,

    /**
     * The new plan takes effect immediately, and the new price will be charged
     * on next recurrence time.
     */
    WithoutProration,

    /**
     * Unknown police.
     */
    Unknown;

    internal fun toReplacementMode(): Int {
        return when (this) {
            ChargeFullPrice -> ReplacementMode.CHARGE_FULL_PRICE
            ChargeProratedPrice -> ReplacementMode.CHARGE_PRORATED_PRICE
            WithTimeProration -> ReplacementMode.WITH_TIME_PRORATION
            Deferred -> ReplacementMode.DEFERRED
            WithoutProration -> ReplacementMode.WITHOUT_PRORATION
            else -> ReplacementMode.UNKNOWN_REPLACEMENT_MODE
        }
    }

    @Suppress("DEPRECATION")
    internal fun toProrationMode(): Int {
        return when (this) {
            ChargeFullPrice -> BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
            ChargeProratedPrice -> BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE
            WithTimeProration -> BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION
            Deferred -> BillingFlowParams.ProrationMode.DEFERRED
            WithoutProration -> BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION
            else -> BillingFlowParams.ProrationMode.UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY
        }
    }

    companion object {

        @Suppress("DEPRECATION")
        fun fromProrationMode(
            @BillingFlowParams.ProrationMode prorationMode: Int?
        ): QPurchaseUpdatePolicy {
            return when (prorationMode) {
                BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE -> ChargeFullPrice
                BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE -> ChargeProratedPrice
                BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION -> WithTimeProration
                BillingFlowParams.ProrationMode.DEFERRED -> Deferred
                BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION -> WithoutProration
                else -> Unknown
            }
        }
    }
}
