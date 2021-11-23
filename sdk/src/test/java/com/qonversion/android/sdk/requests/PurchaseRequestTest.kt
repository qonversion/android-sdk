package com.qonversion.android.sdk.requests

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.old.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.old.converter.Util
import com.qonversion.android.sdk.old.dto.request.PurchaseRequest
import com.qonversion.android.sdk.old.extractor.SkuDetailsTokenExtractor
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PurchaseRequestTest {

    private lateinit var adapter: JsonAdapter<PurchaseRequest>
    private val converter =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())


    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(PurchaseRequest::class.java)
    }

    @Test
    fun purchaseRequestInApp() {

        val purchase = converter.convertPurchase(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_INAPP_JSON),
                Purchase(Util.CORRECT_PURCHASE_INAPP_JSON, "SKU")
            )
        )

        // TODO: Update test for new Purchase request
    }

    @Test
    fun purchaseRequestSub() {

        val purchase = converter.convertPurchase(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_SUB_JSON),
                Purchase(Util.CORRECT_PURCHASE_SUB_JSON, "SKU")
            )
        )

        // TODO: Update test for new Purchase request
    }
}