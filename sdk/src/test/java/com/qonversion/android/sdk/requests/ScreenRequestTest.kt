package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.device.Screen
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenRequestTest {

    private lateinit var adapter: JsonAdapter<Screen>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Screen::class.java)
    }

    @Test
    fun appRequestWithCorrectData() {
        val json = adapter.toJson(Screen(
            width = "screen_width",
            height = "screen_height"
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("width"))
        Assert.assertTrue(jsonObj.has("height"))
        Assert.assertEquals("screen_width", jsonObj.get("width"))
        Assert.assertEquals("screen_height", jsonObj.get("height"))
    }
}