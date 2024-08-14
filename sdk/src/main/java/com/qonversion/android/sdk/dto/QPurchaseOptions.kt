package com.qonversion.android.sdk.dto

import com.qonversion.android.sdk.dto.products.QProduct
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class QPurchaseOptions internal constructor (
    internal val contextKeys: List<String>? = null,
    internal val offerId: String? = null,
    internal val applyOffer: Boolean = true,
    internal val oldProduct: QProduct? = null,
    internal val updatePolicy: QPurchaseUpdatePolicy? = null
) {
    class Builder {
        private var contextKeys: List<String>? = null
        private var offerId: String? = null
        private var applyOffer: Boolean = true
        private var oldProduct: QProduct? = null
        private var updatePolicy: QPurchaseUpdatePolicy? = null

        fun setContextKeys(contextKeys: List<String>): QPurchaseOptions.Builder = apply {
            this.contextKeys = contextKeys
        }

        fun setOfferId(offerId: String): QPurchaseOptions.Builder = apply {
            this.offerId = offerId
        }

        fun setOldProduct(oldProduct: QProduct): QPurchaseOptions.Builder = apply {
            this.oldProduct = oldProduct
        }

        fun setUpdatePolicy(updatePolicy: QPurchaseUpdatePolicy): QPurchaseOptions.Builder = apply {
            this.updatePolicy = updatePolicy
        }

        fun removeOffer() = apply {
            this.applyOffer = false
        }

        fun build(): QPurchaseOptions {
            return QPurchaseOptions(contextKeys, offerId, applyOffer, oldProduct, updatePolicy)
        }
    }
}
