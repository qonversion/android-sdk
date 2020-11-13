package com.qonversion.android.sdk.converter

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_PURCHASE_INAPP_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_PURCHASE_SUB_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_SKU_DETAILS_INAPP_JSON
import com.qonversion.android.sdk.converter.Util.Companion.CORRECT_SKU_DETAILS_SUB_JSON
import com.qonversion.android.sdk.extractor.SkuDetailsTokenExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GooglePurchaseConverterTest {

    @Test
    fun convertCorrectPurchase() {
        val converted = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(CORRECT_SKU_DETAILS_INAPP_JSON),
                    Purchase(CORRECT_PURCHASE_INAPP_JSON, "SKU")
                )
            )

        // TODO: Update test for new Purchase fields
    }

    @Test
    fun convertSubscription() {
        val converted = GooglePurchaseConverter(SkuDetailsTokenExtractor())
            .convert(
                Pair(
                    SkuDetails(CORRECT_SKU_DETAILS_SUB_JSON),
                    Purchase(CORRECT_PURCHASE_SUB_JSON, "SKU")
                )
            )

        // TODO: Update test for new Purchase fields
    }
}