package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.QonversionConfig.Builder
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QProductOfferDetails
import com.qonversion.android.sdk.dto.products.QProductStoreDetails
import com.squareup.moshi.JsonClass

/**
 * Purchase options that may be used to modify purchase process.
 * To create an instance, use the nested [Builder] class.
 */
@JsonClass(generateAdapter = true)
class QPurchaseOptions internal constructor (
    internal val contextKeys: List<String>? = null,
    internal val offerId: String? = null,
    internal val applyOffer: Boolean = true,
    internal val oldProduct: QProduct? = null,
    internal val updatePolicy: QPurchaseUpdatePolicy? = null
) {
    /**
     * The builder of QPurchaseOptions instance.
     *
     * This class contains a variety of methods to customize the purchase behavior.
     * You can call them sequentially and call [build] finally to get the [QPurchaseOptions] instance.
     */
    class Builder {
        private var contextKeys: List<String>? = null
        private var offerId: String? = null
        private var applyOffer: Boolean = true
        private var oldProduct: QProduct? = null
        private var updatePolicy: QPurchaseUpdatePolicy? = null

        /**
         * Set the context keys associated with a purchase.
         *
         * @param contextKeys context keys for the purchase.
         * @return builder instance for chain calls.
         */
        fun setContextKeys(contextKeys: List<String>): QPurchaseOptions.Builder = apply {
            this.contextKeys = contextKeys
        }

        /**
         * Set Qonversion product from which the upgrade/downgrade
         * will be initialized.
         *
         * @param oldProduct Qonversion product from which the upgrade/downgrade
         * will be initialized.
         * @return builder instance for chain calls.
         */
        fun setOldProduct(oldProduct: QProduct): QPurchaseOptions.Builder = apply {
            this.oldProduct = oldProduct
        }

        /**
         * Set the update policy for the purchase.
         * If the [updatePolicy] is not provided, then default one
         * will be selected - [QPurchaseUpdatePolicy.WithTimeProration].
         * @param updatePolicy update policy for the purchase.
         * @return builder instance for chain calls.
         */
        fun setUpdatePolicy(updatePolicy: QPurchaseUpdatePolicy): QPurchaseOptions.Builder = apply {
            this.updatePolicy = updatePolicy
        }

        /**
         * Set offer for the purchase.
         * @param offer concrete offer which you'd like to purchase.
         * @return builder instance for chain calls.
         */
        fun setOffer(offer: QProductOfferDetails): QPurchaseOptions.Builder = apply {
            this.offerId = offer.offerId
        }

        /**
         * Set the offer Id to the purchase.
         * If [offerId] is not specified, then the default offer will be applied. To know how we choose
         * the default offer, see [QProductStoreDetails.defaultSubscriptionOfferDetails].
         * @param offerId concrete offer Id which you'd like to purchase.
         * @return builder instance for chain calls.
         */
        fun setOfferId(offerId: String): QPurchaseOptions.Builder = apply {
            this.offerId = offerId
        }

        /**
         * Call this function to remove any intro/trial offer from the purchase (use only a bare base plan).
         * @return builder instance for chain calls.
         */
        fun removeOffer(): QPurchaseOptions.Builder = apply {
            this.applyOffer = false
        }

        /**
         * Generate [QPurchaseOptions] instance with all the provided options.
         * @return the complete [QPurchaseOptions] instance.
         */
        fun build(): QPurchaseOptions {
            return QPurchaseOptions(contextKeys, offerId, applyOffer, oldProduct, updatePolicy)
        }
    }
}
