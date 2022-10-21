package com.qonversion.android.sdk.internal.requests

import com.qonversion.android.sdk.internal.dto.ProviderData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderDataRequestTest {

    private lateinit var adapter: JsonAdapter<ProviderData>


    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(ProviderData::class.java)
    }

    @Test
    fun providerDataRequestWithCorrectData() {
        val json = adapter.toJson(
            ProviderData(
            data = mutableMapOf(
                "any_string_key" to "any_string_value",
                "any_boolean_key" to true,
                "any_int_key" to 100,
                "any_long_key" to Long.MAX_VALUE,
                "any_double_key" to 1000000.0000
            ),
            provider = "provider_name"
        )
        )

        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("d"))
        Assert.assertTrue(jsonObj.has("provider"))

        val data : JSONObject = jsonObj.get("d") as JSONObject

        Assert.assertTrue(data.has("any_string_key"))
        Assert.assertTrue(data.has("any_boolean_key"))
        Assert.assertTrue(data.has("any_int_key"))
        Assert.assertTrue(data.has("any_long_key"))
        Assert.assertTrue(data.has("any_double_key"))

        Assert.assertEquals(data["any_string_key"] as String, "any_string_value")
        Assert.assertEquals(data["any_boolean_key"] as Boolean, true)
        Assert.assertEquals(data["any_int_key"] as Int, 100)
        Assert.assertEquals(data["any_long_key"] as Long, Long.MAX_VALUE)
        Assert.assertEquals(data["any_double_key"] as Double, 1000000.0000, 0.0)
    }
}