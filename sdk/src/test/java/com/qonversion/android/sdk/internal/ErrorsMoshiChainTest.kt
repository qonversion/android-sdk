package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.qonversion.android.sdk.internal.api.Api
import com.qonversion.android.sdk.internal.di.module.NetworkModule
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Pins which exception types the real Retrofit+Moshi chain (the production converter setup from
 * [NetworkModule]) produces for broken response bodies, and that [Throwable.toQonversionError]
 * classifies both as [QonversionErrorCode.ResponseParsingFailed]:
 *
 * - a type mismatch (valid JSON, wrong shape) surfaces as Moshi's [JsonDataException];
 * - syntactically malformed JSON surfaces as Moshi's [JsonEncodingException], which extends
 *   [java.io.IOException] — so without an explicit branch it would be misreported as
 *   NetworkConnectionFailed and retried forever by fallback logic keyed on that code.
 */
internal class ErrorsMoshiChainTest {
    private lateinit var server: MockWebServer
    private lateinit var api: Api

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(NetworkModule().provideMoshi()))
            .baseUrl(server.url("/").toString())
            .client(OkHttpClient())
            .build()
        api = retrofit.create(Api::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetchRemoteConfigThrowable(body: String): Throwable {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
        try {
            api.remoteConfig("uid", null).execute()
        } catch (e: Exception) {
            return e
        }
        fail("Expected the Moshi converter to throw for body: $body")
        throw AssertionError("unreachable")
    }

    @Test
    fun `a type mismatch reaches the mapper as JsonDataException and maps to ResponseParsingFailed`() {
        val thrown = fetchRemoteConfigThrowable(
            """{"payload": "not an object", "experiment": null, "source": null}"""
        )

        assertTrue(
            "Expected JsonDataException, got ${thrown.javaClass.name}",
            thrown is JsonDataException
        )
        assertEquals(QonversionErrorCode.ResponseParsingFailed, thrown.toQonversionError().code)
    }

    @Test
    fun `malformed JSON reaches the mapper as JsonEncodingException and maps to ResponseParsingFailed`() {
        val thrown = fetchRemoteConfigThrowable("""{"payload": nul}""")

        assertTrue(
            "Expected JsonEncodingException, got ${thrown.javaClass.name}",
            thrown is JsonEncodingException
        )
        assertEquals(QonversionErrorCode.ResponseParsingFailed, thrown.toQonversionError().code)
    }
}
