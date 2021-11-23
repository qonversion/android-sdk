package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.old.dto.app.App
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppRequestTest {

    private lateinit var adapter: JsonAdapter<App>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(App::class.java)
    }

    @Test
    fun appRequestWithCorrectData() {
        val json = adapter.toJson(App(
            name = "app_name",
            build = "app_build",
            bundle = "app_bundle",
            version = "app_version"
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("name"))
        Assert.assertTrue(jsonObj.has("build"))
        Assert.assertTrue(jsonObj.has("bundle"))
        Assert.assertTrue(jsonObj.has("version"))

        Assert.assertEquals("app_name", jsonObj.get("name"))
        Assert.assertEquals("app_build", jsonObj.get("build"))
        Assert.assertEquals("app_bundle", jsonObj.get("bundle"))
        Assert.assertEquals("app_version", jsonObj.get("version"))
    }
}