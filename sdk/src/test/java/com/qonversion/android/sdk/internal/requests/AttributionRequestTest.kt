package com.qonversion.android.sdk.internal.requests

import com.qonversion.android.sdk.internal.dto.request.AttributionRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
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