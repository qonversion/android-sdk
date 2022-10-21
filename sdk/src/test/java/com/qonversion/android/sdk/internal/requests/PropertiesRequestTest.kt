package com.qonversion.android.sdk.internal.requests

import com.qonversion.android.sdk.internal.dto.request.PropertiesRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
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