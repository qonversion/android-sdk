package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.AttributionRequest
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.ProviderData
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
class AttributionRequestTest {

    private lateinit var adapter: JsonAdapter<AttributionRequest>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(AttributionRequest::class.java)
    }

    @Test
    fun appRequestWithCorrectData() {
        // TODO: Update test for new AttributionRequest format
    }
}