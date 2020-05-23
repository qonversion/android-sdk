package com.qonversion.android.sdk.requests

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
class DeviceRequestTest {

    private lateinit var adapter: JsonAdapter<Device>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(Device::class.java)
    }

    @Test
    fun deviceRequestWithCorrectData() {
        val json = adapter.toJson(Device(
            os = Os(),
            ads = AdsDto(true, "edfa"),
            deviceId = "user_device_id",
            model = "user_device_model",
            carrier = "user_device_carrier",
            locale = "user_device_locale",
            screen = Screen("user_device_height","user_device_width"),
            timezone = "user_device_timezone"
        ))
        val jsonObj = JSONObject(json)

        Assert.assertTrue(jsonObj.has("os"))
        Assert.assertTrue(jsonObj.has("ads"))
        Assert.assertTrue(jsonObj.has("deviceId"))
        Assert.assertTrue(jsonObj.has("model"))
        Assert.assertTrue(jsonObj.has("carrier"))
        Assert.assertTrue(jsonObj.has("locale"))
        Assert.assertTrue(jsonObj.has("screen"))
        Assert.assertTrue(jsonObj.has("timezone"))

        Assert.assertEquals("user_device_id", jsonObj.get("deviceId"))
        Assert.assertEquals("user_device_model", jsonObj.get("model"))
        Assert.assertEquals("user_device_carrier", jsonObj.get("carrier"))
        Assert.assertEquals("user_device_locale", jsonObj.get("locale"))
        Assert.assertEquals("user_device_timezone", jsonObj.get("timezone"))
    }
}