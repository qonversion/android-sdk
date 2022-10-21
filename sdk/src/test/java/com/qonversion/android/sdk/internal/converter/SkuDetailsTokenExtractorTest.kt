package com.qonversion.android.sdk.internal.converter

import com.qonversion.android.sdk.internal.converter.Util.Companion.CORRECT_SKU_DETAILS_SUB_JSON
import com.qonversion.android.sdk.internal.extractor.SkuDetailsTokenExtractor
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkuDetailsTokenExtractorTest {

    @Test
    fun extractSkuDetailsTokenFromCorrectJson() {
        val extractor = SkuDetailsTokenExtractor()
        val skuDetailsToken = extractor.extract(CORRECT_SKU_DETAILS_SUB_JSON)
        Assert.assertEquals(skuDetailsToken, "XXXXXXX")
    }

    @Test
    fun extractSkuDetailsTokenFromNullJson() {
        val extractor = SkuDetailsTokenExtractor()
        val skuDetailsToken = extractor.extract(null)
        Assert.assertEquals(skuDetailsToken, "")
    }

    @Test
    fun extractSkuDetailsTokenFromEmptyJson() {
        val extractor = SkuDetailsTokenExtractor()
        val skuDetailsToken = extractor.extract("{}")
        Assert.assertEquals(skuDetailsToken, "")
    }
}