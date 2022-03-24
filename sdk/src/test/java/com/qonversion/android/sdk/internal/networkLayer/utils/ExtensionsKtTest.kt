package com.qonversion.android.sdk.internal.networkLayer.utils

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class ExtensionsKtTest {

    @Test
    fun `JSONObject to map with values`() {
        // given
        val expectedValues = mapOf(
            "one" to "three",
            "to be or not" to "be"
        )
        val obj = JSONObject().apply {
            expectedValues.forEach {
                put(it.key, it.value)
            }
        }

        // when
        val res = obj.toMap()

        // then
        assertThat(res).isEqualTo(expectedValues)
    }

    @Test
    fun `JSONObject to map without values`() {
        // given
        val obj = JSONObject()

        // when
        val res = obj.toMap()

        // then
        assertThat(res).isEmpty()
    }

    @Test
    fun `JSONArray to list with values`() {
        // given
        val expectedValues = listOf("one", 2, 3.0)
        val arr = JSONArray().apply {
            expectedValues.forEach {
                put(it)
            }
        }

        // when
        val res = arr.toList()

        // then
        assertThat(res).isEqualTo(expectedValues)
    }

    @Test
    fun `JSONArray to list without values`() {
        // given
        val arr = JSONArray()

        // when
        val res = arr.toList()

        // then
        assertThat(res).isEmpty()
    }

    @Test
    fun `parse list JSON value`() {
        // given
        val expectedValues = listOf("one", 2, 3.0)
        val arr = JSONArray().apply {
            expectedValues.forEach {
                put(it)
            }
        }

        // when
        val res = arr.parseJsonValue()

        // then
        assertThat(res).isEqualTo(expectedValues)
    }

    @Test
    fun `parse map JSON value`() {
        // given
        val expectedValues = mapOf(
            "one" to "three",
            "to be or not" to "be"
        )
        val obj = JSONObject().apply {
            expectedValues.forEach {
                put(it.key, it.value)
            }
        }

        // when
        val res = obj.parseJsonValue()

        // then
        assertThat(res).isEqualTo(obj)
    }

    @Test
    fun `parse simple JSON value`() {
        // given
        val value = 3

        // when
        val res = value.parseJsonValue()

        // then
        assertThat(res).isEqualTo(value)
    }

    @Test
    fun `is successful http code`() {
        assertThat(100.isSuccessHttpCode).isFalse
        assertThat(199.isSuccessHttpCode).isFalse
        assertThat(200.isSuccessHttpCode).isTrue
        assertThat(250.isSuccessHttpCode).isTrue
        assertThat(299.isSuccessHttpCode).isTrue
        assertThat(300.isSuccessHttpCode).isFalse
        assertThat(400.isSuccessHttpCode).isFalse
        assertThat(500.isSuccessHttpCode).isFalse
        assertThat((-1).isSuccessHttpCode).isFalse
    }

    @Test
    fun `is internal server error http code`() {
        assertThat(100.isInternalServerErrorHttpCode).isFalse
        assertThat(200.isInternalServerErrorHttpCode).isFalse
        assertThat(300.isInternalServerErrorHttpCode).isFalse
        assertThat(400.isInternalServerErrorHttpCode).isFalse
        assertThat(499.isInternalServerErrorHttpCode).isFalse
        assertThat(500.isInternalServerErrorHttpCode).isTrue
        assertThat(550.isInternalServerErrorHttpCode).isTrue
        assertThat(599.isInternalServerErrorHttpCode).isTrue
        assertThat(600.isInternalServerErrorHttpCode).isFalse
        assertThat((-1).isInternalServerErrorHttpCode).isFalse
    }
}