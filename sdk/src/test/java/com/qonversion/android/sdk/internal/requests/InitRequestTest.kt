package com.qonversion.android.sdk.internal.requests

import com.qonversion.android.sdk.internal.dto.request.InitRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InitRequestTest {

    private lateinit var adapter: JsonAdapter<InitRequest>

    @Before
    fun setup() {
        val moshi = Moshi.Builder().build()
        adapter = moshi.adapter(InitRequest::class.java)
    }

    @Test
    fun appRequestWithCorrectData() {
        // TODO: Update test for new InitRequest format
    }
}