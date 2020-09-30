package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.PropertiesRequest
import com.qonversion.android.sdk.dto.app.App
import com.qonversion.android.sdk.dto.device.AdsDto
import com.qonversion.android.sdk.dto.device.Device
import com.qonversion.android.sdk.dto.device.Os
import com.qonversion.android.sdk.dto.device.Screen
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class PropertiesRequestTest {
    private lateinit var adapter: JsonAdapter<PropertiesRequest>

    @Before
    fun setUp(){
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(PropertiesRequest::class.java)
    }

    @Test
    fun requestWithCorrectData(){
        val json = adapter.toJson(PropertiesRequest(
            d = Environment(
                internalUserID = "internal_user_id",
                app = App(
                    name = "app_name",
                    version = "app_version",
                    build = "app_build",
                    bundle = "app_bundle"
                ),
                device = Device(
                    os = Os(),
                    ads = AdsDto(true, "edfa"),
                    deviceId = "user_device_id",
                    model = "user_device_model",
                    carrier = "user_device_carrier",
                    locale = "user_device_locale",
                    screen = Screen("user_device_height","user_device_width"),
                    timezone = "user_device_timezone"
                )
            ),
            v = "version",
            accessToken = "user_access_token",
            clientUid = "user_client_uid",
            properties = mutableMapOf(
                "any_string_key_1" to "any_string_value_1",
                "any_string_key_2" to "any_string_value_2"
            )

        ))
        val jsonObj = JSONObject(json)
        Assert.assertTrue(jsonObj.has("d"))
        Assert.assertTrue(jsonObj.has("v"))
        Assert.assertTrue(jsonObj.has("access_token"))
        Assert.assertTrue(jsonObj.has("client_uid"))
        Assert.assertTrue(jsonObj.has("properties"))

        Assert.assertEquals("user_access_token", jsonObj.get("access_token"))
        Assert.assertEquals("user_client_uid", jsonObj.get("client_uid"))
        Assert.assertEquals("version", jsonObj.get("v"))

        val properties : JSONObject = jsonObj.get("properties") as JSONObject

        Assert.assertTrue(properties.has("any_string_key_1"))
        Assert.assertTrue(properties.has("any_string_key_2"))

        Assert.assertEquals(properties["any_string_key_1"] as String, "any_string_value_1")
        Assert.assertEquals(properties["any_string_key_2"] as String, "any_string_value_2")
    }
}