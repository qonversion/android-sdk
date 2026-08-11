package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.QonversionErrorCode
import com.squareup.moshi.JsonDataException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Pins the [Throwable.toQonversionError] mapping — most importantly that Moshi's
 * [JsonDataException] (a strict-parsing failure, e.g. an invalid remoteConfigList element) surfaces
 * as [QonversionErrorCode.ResponseParsingFailed] rather than falling through to Unknown.
 */
internal class ErrorsTest {

    @Test
    fun `moshi JsonDataException maps to ResponseParsingFailed`() {
        val error = JsonDataException("Expected a string but was BEGIN_OBJECT").toQonversionError()

        assertEquals(QonversionErrorCode.ResponseParsingFailed, error.code)
        assertEquals("Expected a string but was BEGIN_OBJECT", error.additionalMessage)
    }

    @Test
    fun `JSONException maps to ResponseParsingFailed`() {
        val error = JSONException("Unterminated object").toQonversionError()

        assertEquals(QonversionErrorCode.ResponseParsingFailed, error.code)
    }

    @Test
    fun `IOException maps to NetworkConnectionFailed`() {
        val error = IOException("timeout").toQonversionError()

        assertEquals(QonversionErrorCode.NetworkConnectionFailed, error.code)
    }

    @Test
    fun `an unrecognized throwable maps to Unknown`() {
        val error = IllegalStateException("boom").toQonversionError()

        assertEquals(QonversionErrorCode.Unknown, error.code)
    }
}
