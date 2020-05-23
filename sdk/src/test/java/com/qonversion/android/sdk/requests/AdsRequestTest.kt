package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.device.AdsDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdsRequestTest {

    private lateinit var adapter: JsonAdapter<AdsDto>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(AdsDto::class.java)
    }

    @Test
    fun adsRequestWithNullEdfa() {
        val json = adapter.toJson(AdsDto(trackingEnabled = true, edfa = null))
        val jsonObj = JSONObject(json)
        Assert.assertTrue(jsonObj.has("trackingEnabled"))
        Assert.assertFalse(jsonObj.has("edfa"))
        Assert.assertEquals(true, jsonObj.get("trackingEnabled"))
        Assert.assertTrue(jsonObj.isNull("edfa"))
    }

    @Test
    fun adsRequestWithCorrectData() {
        val json = adapter.toJson(AdsDto(trackingEnabled = true, edfa = "edfa"))
        val jsonObj = JSONObject(json)
        Assert.assertTrue(jsonObj.has("trackingEnabled"))
        Assert.assertTrue(jsonObj.has("edfa"))
        Assert.assertEquals(true, jsonObj.get("trackingEnabled"))
        Assert.assertEquals("edfa", jsonObj.get("edfa"))
    }
}