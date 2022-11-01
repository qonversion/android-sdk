package com.qonversion.android.sdk.internal.requests

import android.os.Build
import com.qonversion.android.sdk.internal.dto.device.Os
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class OsRequestTest {

    private lateinit var adapter: JsonAdapter<Os>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Os::class.java)
    }

    @Test
    fun osRequestWithCorrectData() {
        val json = adapter.toJson(Os())
        val jsonObj = JSONObject(json)
        Assert.assertTrue(jsonObj.has("version"))
        Assert.assertTrue(jsonObj.has("name"))
        Assert.assertEquals("Android", jsonObj.get("name"))
        Assert.assertEquals(Build.VERSION.SDK_INT.toString(), jsonObj.get("version"))
    }
}