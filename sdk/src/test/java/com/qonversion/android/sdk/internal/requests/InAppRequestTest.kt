package com.qonversion.android.sdk.internal.requests

import android.util.Pair
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.qonversion.android.sdk.internal.converter.GooglePurchaseConverter
import com.qonversion.android.sdk.internal.converter.Util
import com.qonversion.android.sdk.internal.dto.purchase.Inapp
import com.qonversion.android.sdk.internal.extractor.SkuDetailsTokenExtractor
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class InAppRequestTest {

    private lateinit var adapter: JsonAdapter<Inapp>
    private val converter =
        GooglePurchaseConverter(SkuDetailsTokenExtractor())

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Inapp::class.java)
    }

    @Test
    fun inAppRequestInAppWithCorrectData() {

        val purchase = converter.convertPurchase(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_INAPP_JSON),
                Purchase(Util.CORRECT_PURCHASE_INAPP_JSON, "SKU")
            )
        )

        // TODO: Update test for new InApp format
    }

    @Test
    fun inAppRequestSubWithCorrectData() {

        val purchase = converter.convertPurchase(
            Pair(
                SkuDetails(Util.CORRECT_SKU_DETAILS_SUB_JSON),
                Purchase(Util.CORRECT_PURCHASE_SUB_JSON, "SKU")
            )
        )

        // TODO: Update test for new InApp format
    }
}