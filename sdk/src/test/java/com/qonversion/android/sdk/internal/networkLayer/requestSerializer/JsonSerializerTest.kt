package com.qonversion.android.sdk.internal.networkLayer.requestSerializer

import com.qonversion.android.sdk.internal.exception.QonversionException
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class JsonSerializerTest {

    private lateinit var jsonSerializer: JsonSerializer

    @Before
    fun setUp() {
        jsonSerializer = JsonSerializer()
    }

    @Test
    fun `serialize simple map`() {
        // given
        val data = mapOf("key" to "value")

        // when
        val result = jsonSerializer.serialize(data)

        // then
        assertThat(result).isEqualTo("""{"key":"value"}""")
    }

    @Test
    fun `serialize nested array`() {
        // given
        val data = mapOf("key" to listOf(1, 2, 3))

        // when
        val result = jsonSerializer.serialize(data)

        // then
        assertThat(result).isEqualTo("""{"key":[1,2,3]}""")
    }

    @Test
    fun `serialize nested map`() {
        // given
        val data = mapOf("key" to mapOf("nestedKey" to "value"))

        // when
        val result = jsonSerializer.serialize(data)

        // then
        assertThat(result).isEqualTo("""{"key":{"nestedKey":"value"}}""")
    }

    @Test
    fun `serialize null value`() {
        // given
        val data = mapOf("key" to null)

        // when
        val result = jsonSerializer.serialize(data)

        // then
        assertThat(result).isEqualTo("""{}""")
    }

    @Test
    fun `serialize null key`() {
        // given
        val nestedMap = mutableMapOf<String?, Int>().apply {
            put(null, 1)
        }
        val data = mapOf("a" to "b", "key" to nestedMap)

        // when and then
        assertThatThrownBy {
            jsonSerializer.serialize(data)
        }.isInstanceOf(QonversionException::class.java)
    }

    @Test
    fun `deserialize correct json`() {
        // given
        val json = """{"field1": "value1"}"""
        val expected = mapOf("field1" to "value1")

        // when
        val res = jsonSerializer.deserialize(json)

        // then
        assertThat(res).isEqualTo(expected)
    }

    @Test
    fun `deserialize array json`() {
        // given
        val json = """["value1", "value2"]"""
        val expected = listOf("value1", "value2")

        // when
        val res = jsonSerializer.deserialize(json)

        // then
        assertThat(res).isEqualTo(expected)
    }

    @Test
    fun `deserialize array with nested json`() {
        // given
        val json = """["value", {"field1": "value1"}]"""
        val expected = listOf("value", mapOf("field1" to "value1"))

        // when
        val res = jsonSerializer.deserialize(json)

        // then
        assertThat(res).isEqualTo(expected)
    }

    @Test
    fun `deserialize json with nested array`() {
        // given
        val json = """{"field1": ["value1", "value2"]}"""
        val expected = mapOf("field1" to listOf("value1", "value2"))

        // when
        val res = jsonSerializer.deserialize(json)

        // then
        assertThat(res).isEqualTo(expected)
    }

    @Test
    fun `deserialize incorrect json`() {
        // given
        val json = """("field1": "value1")"""

        // when and then
        assertThatThrownBy {
            jsonSerializer.deserialize(json)
        }.isInstanceOf(QonversionException::class.java)
    }

    @Test
    fun `deserialize json with nested error`() {
        // given
        val json = """{"key": {"nested": ("field1": "value1")}}"""

        // when and then
        assertThatThrownBy {
            jsonSerializer.deserialize(json)
        }.isInstanceOf(QonversionException::class.java)
    }

    @Test
    fun `deserialize empty string`() {
        // given
        val json1 = "\"\""
        val json2 = ""

        // when and then
        assertThatThrownBy {
            jsonSerializer.deserialize(json1)
        }.isInstanceOf(QonversionException::class.java)
        assertThatThrownBy {
            jsonSerializer.deserialize(json2)
        }.isInstanceOf(QonversionException::class.java)
    }

    @Test
    fun `deserialize empty json`() {
        // given
        val json = """{}"""

        // when
        val res = jsonSerializer.deserialize(json)

        // then
        assertThat(res).isEqualTo(emptyMap<Any?, Any?>())
    }
}