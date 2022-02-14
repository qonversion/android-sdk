package com.qonversion.android.sdk.internal.common.mappers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

internal class MapDataMapperTest {
    private lateinit var mapper: MapDataMapper

    @BeforeEach
    fun setUp() {
        mapper = MapDataMapper()
    }

    @Nested
    inner class FromMap {

        @Test
        fun `from map success`() {
            // given
            val map = mapOf("key1" to "value1", "key2" to "value2")
            val expectedResult = "{\"key1\":\"value1\",\"key2\":\"value2\"}"

            // when
            val result = mapper.fromMap(map)

            // then
            assertThat(result).isEqualTo(expectedResult)
        }

        @Test
        fun `from map when exception occurred`() {
            // given
            val map = mapOf(null to "value1", "key2" to "value2")

            // when
            val e = try {
                mapper.fromMap(map)
                null
            } catch (e: Exception) {
                e
            }

            // then
            assertThat(e).isNotNull
            assertThat(e).isInstanceOf(IllegalStateException::class.java)
            assertThat(e?.message).isEqualTo("Couldn't create JSONObject from map")
        }
    }

    @Nested
    inner class ToMap {

        @Test
        fun `to map success with strings`() {
            // given
            val jsonString = "{\"key1\":\"value1\",\"key2\":\"value2\"}"
            val expectedResult = mapOf("key1" to "value1", "key2" to "value2")

            // when
            val result = mapper.toMap(jsonString)

            // then
            assertThat(result).isEqualTo(expectedResult)
        }

        @Test
        fun `to map when exception occurred`() {
            // given
            val jsonString = "{\"key1lue1\",\"key2\":\"value2\"}"

            // when
            val e = try {
                mapper.toMap(jsonString)
                null
            } catch (e: Exception) {
                e
            }

            // then
            assertThat(e).isNotNull
            assertThat(e).isInstanceOf(IllegalStateException::class.java)
            assertThat(e?.message).isEqualTo("Couldn't create JSONObject from string")
        }
    }
}