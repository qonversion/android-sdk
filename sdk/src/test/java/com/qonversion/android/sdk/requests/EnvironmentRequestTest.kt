package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.Environment
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnvironmentRequestTest {

    private lateinit var adapter: JsonAdapter<Environment>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Environment::class.java)
    }

    @Test
    fun appRequestWithCorrectData() {
        val json = adapter.toJson(Environment(
            internalUserID = "internal_user_id",
            app = App(
                name = "app_name",
                build = "app_build",
                bundle = "app_bundle",
                version = "app_version"
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
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("internalUserID"))
        Assert.assertTrue(jsonObj.has("app"))
        Assert.assertTrue(jsonObj.has("device"))
        Assert.assertEquals("internal_user_id", jsonObj.get("internalUserID"))
    }
}