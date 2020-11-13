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
        // TODO: Update test for new PropertiesRequest format
    }
}