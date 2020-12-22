package com.qonversion.android.sdk.requests

import com.qonversion.android.sdk.dto.Environment
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
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
        // TODO: Update test for new Environment format
    }
}