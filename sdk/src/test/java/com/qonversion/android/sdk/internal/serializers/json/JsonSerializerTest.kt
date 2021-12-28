package com.qonversion.android.sdk.internal.serializers.json

import com.qonversion.android.sdk.internal.exception.QonversionException
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class JsonSerializerTest {

    private lateinit var jsonSerializer: JsonSerializer

    private val serializationTests = mapOf(
        mapOf("key" to "value") to """{"key":"value"}""", // simple map
        mapOf("key" to listOf(1, 2, 3)) to """{"key":[1,2,3]}""", // nested array
        mapOf("key" to mapOf("nestedKey" to "value")) to """{"key":{"nestedKey":"value"}}""", // nested map
        mapOf("key" to null) to "{}", // null value
        mapOf("key1" to null, "key2" to "value") to """{"key2":"value"}""" // null and non-null values
    )

    private val deserializationTests = mapOf(
        """{"field1": "value1"}""" to mapOf("field1" to "value1"), // correct json
        """["value1", "value2"]""" to listOf("value1", "value2"), // array json
        """["value", {"field1": "value1"}]""" to listOf("value", mapOf("field1" to "value1")), // array with nested json
        """{"field1": ["value1", "value2"]}""" to mapOf("field1" to listOf("value1", "value2")), // json with nested array
        """{}""" to emptyMap<Any?, Any?>() // empty json
    )

    @Before
    fun setUp() {
        jsonSerializer = JsonSerializerImpl()
    }

    @Test
    fun `serialize tests`() {
        for ((given, expected) in serializationTests) {
            // when
            val result = jsonSerializer.serialize(given)

            // then
            assertThat(result).isEqualTo(expected)
        }
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
    fun `deserialize tests`() {
        for ((given, expected) in deserializationTests) {
            // when
            val result = jsonSerializer.deserialize(given)

            // then
            assertThat(result).isEqualTo(expected)
        }
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
}