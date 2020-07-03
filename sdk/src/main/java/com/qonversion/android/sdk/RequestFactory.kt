package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.*
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.purchase.Inapp
import com.qonversion.android.sdk.entity.Purchase

class RequestFactory(
    private val environmentProvider: EnvironmentProvider,
    private val trackingEnabled: Boolean,
    private val internalUserId: String,
    private val sdkVersion: String,
    private val key: String
) {

    fun createInitRequest(edfa: String?, uid: String): InitRequest {
        return InitRequest(
            d = createEnvironment(createAdsDto(edfa = edfa)),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid
        )
    }

    fun createPurchaseRequest(purchase: Purchase, uid: String): PurchaseRequest {
        return PurchaseRequest(
            d = createEnvironment(createAdsDto(edfa = null)),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            inapp = Inapp(
                detailsToken = purchase.detailsToken,
                title = purchase.title,
                description = purchase.description,
                productId = purchase.productId,
                type = purchase.type,
                originalPrice = purchase.originalPrice,
                originalPriceAmountMicros = purchase.originalPriceAmountMicros,
                priceCurrencyCode = purchase.priceCurrencyCode,
                price = purchase.price,
                priceAmountMicros = purchase.priceAmountMicros,
                subscriptionPeriod = purchase.subscriptionPeriod,
                freeTrialPeriod = purchase.freeTrialPeriod,
                introductoryPriceAmountMicros = purchase.introductoryPriceAmountMicros,
                introductoryPricePeriod = purchase.introductoryPricePeriod,
                introductoryPrice = purchase.introductoryPrice,
                introductoryPriceCycles = purchase.introductoryPriceCycles,
                orderId = purchase.orderId,
                packageName = purchase.packageName,
                purchaseTime = purchase.purchaseTime,
                purchaseState = purchase.purchaseState,
                purchaseToken = purchase.purchaseToken,
                acknowledged = purchase.acknowledged,
                autoRenewing = purchase.autoRenewing
            )
        )
    }

    fun createAttributionRequest(
        conversionInfo: Map<String, Any>,
        from: String,
        conversionUid: String,
        uid: String
    ): QonversionRequest {
        return AttributionRequest(
            d = createEnvironment(createAdsDto(edfa = null)),
            v = sdkVersion,
            accessToken = key,
            clientUid = uid,
            providerData = ProviderData(
                data = conversionInfo,
                provider = from,
                uid = conversionUid
            )
        )
    }

    private fun createAdsDto(edfa: String?): AdsDto {
         return AdsDto(trackingEnabled, edfa = edfa)
    }

    private fun createEnvironment(adsDto: AdsDto): Environment {
        return environmentProvider.getInfo(
            internalUserId,
            adsDto
        )
    }
}