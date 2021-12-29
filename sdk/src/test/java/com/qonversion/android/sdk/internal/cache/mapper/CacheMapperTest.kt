package com.qonversion.android.sdk.internal.cache.mapper

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

internal class CacheMapperTest {

    private lateinit var cacheMapper: CacheMapper<String>
    private val mockJsonSerializer = mockk<Serializer>()
    private val mockMapper = mockk<Mapper<String>>()

    @BeforeEach
    fun setUp() {
        cacheMapper = CacheMapperImpl(mockJsonSerializer, mockMapper)
    }

    @Nested
    inner class ToJsonTest {

        private val testObject = "test object"

        @Test
        fun `to json success`() {
            // given
            val map = mockk<Map<String, Any?>>()
            val expResult = "some json"
            every { mockMapper.toMap(testObject) } returns map
            every { mockJsonSerializer.serialize(map) } returns expResult

            // when
            val result = cacheMapper.toJson(testObject)

            // then
            assertThat(result).isEqualTo(expResult)
            verify(exactly = 1) {
                mockMapper.toMap(testObject)
                mockJsonSerializer.serialize(map)
            }
        }

        @Test
        fun `mapper throws exception`() {
            // given
            every { mockMapper.toMap(testObject) } throws IllegalStateException()

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Serialization) {
                cacheMapper.toJson(testObject)
            }

            // then
            verify(exactly = 1) { mockMapper.toMap(testObject) }
            verify(exactly = 0) { mockJsonSerializer.serialize(any()) }
        }

        @Test
        fun `json serializer throws exception`() {
            // given
            val map = mockk<Map<String, Any?>>()
            val exception = QonversionException(ErrorCode.Serialization)
            every { mockMapper.toMap(testObject) } returns map
            every { mockJsonSerializer.serialize(map) } throws exception

            // when
            val e = assertThatQonversionExceptionThrown {
                cacheMapper.toJson(testObject)
            }

            // then
            assertThat(e === exception)
            verify(exactly = 1) {
                mockMapper.toMap(testObject)
                mockJsonSerializer.serialize(map)
            }
        }
    }

    @Nested
    inner class FromJsonTest {

        private val testJson = "test json object"

        @Test
        fun `from json success`() {
            // given
            val map = mockk<Map<String, Any?>>()
            val expResult = "some object"
            every { mockJsonSerializer.deserialize(testJson) } returns map
            every { mockMapper.fromMap(map) } returns expResult

            // when
            val result = cacheMapper.fromJson(testJson)

            // then
            assertThat(result).isEqualTo(expResult)
            verify(exactly = 1) {
                mockJsonSerializer.deserialize(testJson)
                mockMapper.fromMap(map)
            }
        }

        @Test
        fun `mapper throws exception`() {
            // given
            val map = mockk<Map<String, Any?>>()
            every { mockJsonSerializer.deserialize(testJson) } returns map
            every { mockMapper.fromMap(map) } throws IllegalStateException()

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromJson(testJson)
            }

            // then
            verify(exactly = 1) {
                mockJsonSerializer.deserialize(testJson)
                mockMapper.fromMap(map)
            }
        }

        @Test
        fun `json serializer throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Deserialization)
            every { mockJsonSerializer.deserialize(testJson) } throws exception

            // when
            val e = assertThatQonversionExceptionThrown {
                cacheMapper.fromJson(testJson)
            }

            // then
            assertThat(e === exception)
            verify(exactly = 1) { mockJsonSerializer.deserialize(testJson) }
            verify(exactly = 0) { mockMapper.fromMap(any()) }
        }

        @Test
        fun `json serializer returns not map`() {
            // given
            val parsedResult = "string"
            every { mockJsonSerializer.deserialize(testJson) } returns parsedResult

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromJson(testJson)
            }

            // then
            verify(exactly = 1) { mockJsonSerializer.deserialize(testJson) }
            verify(exactly = 0) { mockMapper.fromMap(any()) }
        }
    }
}