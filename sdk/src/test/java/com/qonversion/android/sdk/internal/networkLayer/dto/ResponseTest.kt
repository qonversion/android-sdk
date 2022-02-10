package com.qonversion.android.sdk.internal.networkLayer.dto

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ResponseTest {

    @Nested
    inner class SuccessTest {

        @Test
        fun `get correct typed data`() {
            // given
            val data = "test data"
            val response = Response.Success(200, data)

            // when
            val result = response.getTypedData<String>()

            // then
            assertThat(result).isSameAs(data)
        }

        @Test
        fun `get incorrect type data`() {
            // given
            val data = "test data"
            val response = Response.Success(200, data)

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.Mapping) {
                response.getTypedData<Long>()
            }
        }

        @Test
        fun `get map data`() {
            // given
            val data = mapOf<String, Long>("one" to 3)
            val response = Response.Success(200, data)

            // when
            val result = response.mapData

            // then
            assertThat(result).isSameAs(data)
        }
    }
}