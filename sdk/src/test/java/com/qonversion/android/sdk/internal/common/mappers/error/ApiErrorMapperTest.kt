package com.qonversion.android.sdk.internal.common.mappers.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

internal class ApiErrorMapperTest {

    private lateinit var errorMapper: ApiErrorMapper

    private val testMessage = "test message"
    private val testType = "test type"
    private val testApiCode = "test code"
    private val testHttpCode = 400

    @Before
    fun setUp() {
        errorMapper = ApiErrorMapper()
    }

    @Test
    fun `complete error`() {
        // given
        val data = mapOf(
            "message" to testMessage,
            "type" to testType,
            "code" to testApiCode
        )

        // when
        val error = errorMapper.fromMap(data, testHttpCode)

        // then
        assertThat(error.code).isEqualTo(testHttpCode)
        assertThat(error.message).isEqualTo(testMessage)
        assertThat(error.type).isEqualTo(testType)
        assertThat(error.apiCode).isEqualTo(testApiCode)
    }

    @Test
    fun `empty error`() {
        // given
        val data = emptyMap<Any?, Any?>()

        // when
        val error = errorMapper.fromMap(data, testHttpCode)

        // then
        assertThat(error.code).isEqualTo(testHttpCode)
        assertThat(error.message).isNotEmpty()
        assertThat(error.type).isNull()
        assertThat(error.apiCode).isNull()
    }

    @Test
    fun `unexpected data in error`() {
        // given
        val testCode = 777
        val data = mapOf(
            "message" to testMessage,
            "type" to 42,
            1 to 3
        )

        // when
        val error = errorMapper.fromMap(data, testCode)

        // then
        assertThat(error.code).isEqualTo(testCode)
        assertThat(error.message).isEqualTo(testMessage)
        assertThat(error.type).isNull()
        assertThat(error.apiCode).isNull()
    }
}
