package com.qonversion.android.sdk.internal.cache.mapper

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.cache.CachedObject
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException
import java.util.Calendar

private typealias NestedObjectType = String

internal class CacheMapperTest {

    private lateinit var cacheMapper: CacheMapper<NestedObjectType>
    private val mockSerializer = mockk<Serializer>()
    private val mockMapper = mockk<Mapper<NestedObjectType>>()

    @BeforeEach
    fun setUp() {
        cacheMapper = CacheMapperImpl(mockSerializer, mockMapper)
    }

    @Nested
    inner class ToSerializedStringTest {

        private val nestedObject: NestedObjectType = ""

        @Test
        fun `successful conversion`() {
            // given
            val timestamp = Calendar.getInstance().time
            val cachedObject = CachedObject(timestamp, nestedObject)

            val nestedObjectMap = mapOf("1" to 3)
            every { mockMapper.toMap(nestedObject) } returns nestedObjectMap

            val slotSerializingMap = slot<Map<String, *>>()
            val expectedResult = "expected result"
            every { mockSerializer.serialize(capture(slotSerializingMap)) } answers { expectedResult }

            val expectedSerializingMap = mapOf(
                "timestamp" to timestamp.time,
                "object" to nestedObjectMap
            )

            // when
            val result = cacheMapper.toSerializedString(cachedObject)

            // then
            assertThat(result).isEqualTo(expectedResult)
            verify(exactly = 1) {
                mockMapper.toMap(nestedObject)
                mockSerializer.serialize(any())
            }
            assertThat(slotSerializingMap.captured).isEqualTo(expectedSerializingMap)
        }

        @Test
        fun `nested object is null`() {
            // given
            val timestamp = Calendar.getInstance().time
            val cachedObject = CachedObject(timestamp, null as NestedObjectType?)

            val slotSerializingMap = slot<Map<String, *>>()
            val expectedResult = "expected result"
            every { mockSerializer.serialize(capture(slotSerializingMap)) } answers { expectedResult }

            val expectedSerializingMap = mapOf(
                "timestamp" to timestamp.time,
                "object" to null
            )

            // when
            val result = cacheMapper.toSerializedString(cachedObject)

            // then
            assertThat(result).isEqualTo(expectedResult)
            verify(exactly = 1) { mockSerializer.serialize(any()) }
            verify(exactly = 0) { mockMapper.toMap(any()) }
            assertThat(slotSerializingMap.captured).isEqualTo(expectedSerializingMap)
        }

        @Test
        fun `mapper throws exception`() {
            // given
            val timestamp = Calendar.getInstance().time
            val cachedObject = CachedObject(timestamp, nestedObject)

            every { mockMapper.toMap(nestedObject) } throws IllegalStateException()

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Serialization) {
                cacheMapper.toSerializedString(cachedObject)
            }

            // then
            verify(exactly = 1) { mockMapper.toMap(nestedObject) }
            verify(exactly = 0) { mockSerializer.serialize(any()) }
        }

        @Test
        fun `serializer throws exception`() {
            // given
            val timestamp = Calendar.getInstance().time
            val cachedObject = CachedObject(timestamp, nestedObject)

            val nestedObjectMap = mapOf("1" to 3)
            every { mockMapper.toMap(nestedObject) } returns nestedObjectMap

            val slotSerializingMap = slot<Map<String, *>>()
            val exception = QonversionException(ErrorCode.Serialization)
            every { mockSerializer.serialize(capture(slotSerializingMap)) } throws exception

            val expectedSerializingMap = mapOf(
                "timestamp" to timestamp.time,
                "object" to nestedObjectMap
            )

            // when
            val e = assertThatQonversionExceptionThrown {
                cacheMapper.toSerializedString(cachedObject)
            }

            // then
            assertThat(e === exception)
            verify(exactly = 1) {
                mockMapper.toMap(nestedObject)
                mockSerializer.serialize(any())
            }
            assertThat(slotSerializingMap.captured).isEqualTo(expectedSerializingMap)
        }
    }

    @Nested
    inner class FromSerializedStringTest {

        private val deserializingValue = "deserializing value"
        private val nestedObject: NestedObjectType = "nested object"

        @Test
        fun `successful conversion`() {
            // given
            val timestamp = Calendar.getInstance().time
            val nestedObjectMap = mapOf("1" to 3)
            val deserializedMap = mapOf("timestamp" to timestamp.time, "object" to nestedObjectMap)
            every { mockSerializer.deserialize(deserializingValue) } returns deserializedMap

            every { mockMapper.fromMap(nestedObjectMap) } returns nestedObject

            // when
            val result = cacheMapper.fromSerializedString(deserializingValue)

            // then
            assertThat(result.value === nestedObject)
            assertThat(result.date.time).isEqualTo(timestamp.time)
            verify(exactly = 1) {
                mockSerializer.deserialize(deserializingValue)
                mockMapper.fromMap(nestedObjectMap)
            }
        }

        @Test
        fun `conversion with unknown nested value`() {
            val timestamp = Calendar.getInstance().time
            listOf(
                mapOf("timestamp" to timestamp.time, "object" to null),
                mapOf("timestamp" to timestamp.time)
            ).forEach { deserializedMap ->
                // given
                clearMocks(mockSerializer, mockMapper)
                every { mockSerializer.deserialize(deserializingValue) } returns deserializedMap

                // when
                val result = cacheMapper.fromSerializedString(deserializingValue)

                // then
                assertThat(result.value).isNull()
                assertThat(result.date.time).isEqualTo(timestamp.time)
                verify(exactly = 1) { mockSerializer.deserialize(deserializingValue) }
                verify(exactly = 0) { mockMapper.fromMap(any()) }
            }
        }

        @Test
        fun `serializer throws exception`() {
            // given
            val exception = QonversionException(ErrorCode.Deserialization)
            every { mockSerializer.deserialize(deserializingValue) } throws exception

            // when
            val e = assertThatQonversionExceptionThrown {
                cacheMapper.fromSerializedString(deserializingValue)
            }

            // then
            assertThat(e === exception)
            verify(exactly = 1) { mockSerializer.deserialize(deserializingValue) }
            verify(exactly = 0) { mockMapper.fromMap(any()) }
        }

        @Test
        fun `serializer returns not map`() {
            // given
            every { mockSerializer.deserialize(deserializingValue) } returns listOf(1)

            // when
            assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromSerializedString(deserializingValue)
            }

            // then
            verify(exactly = 1) { mockSerializer.deserialize(deserializingValue) }
            verify(exactly = 0) { mockMapper.fromMap(any()) }
        }

        @Test
        fun `timestamp is not long`() {
            // given
            val nestedObjectMap = mapOf("1" to 3)
            val deserializedMap = mapOf("timestamp" to "deception", "object" to nestedObjectMap)
            every { mockSerializer.deserialize(deserializingValue) } returns deserializedMap

            every { mockMapper.fromMap(nestedObjectMap) } returns nestedObject

            // when
            val e = assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromSerializedString(deserializingValue)
            }

            // then
            assertThat(e.message).isEqualTo("Unexpected data type")
            verify(exactly = 1) { mockSerializer.deserialize(deserializingValue) }
        }

        @Test
        fun `nested object is not map`() {
            // given
            val timestamp = Calendar.getInstance().time
            val deserializedMap = mapOf("timestamp" to timestamp.time, "object" to "What???")
            every { mockSerializer.deserialize(deserializingValue) } returns deserializedMap

            // when
            val e = assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromSerializedString(deserializingValue)
            }

            // then
            assertThat(e.message).isEqualTo("Unexpected data type")
            verify(exactly = 1) { mockSerializer.deserialize(deserializingValue) }
            verify(exactly = 0) { mockMapper.fromMap(any()) }
        }

        @Test
        fun `mapper throws exception`() {
            // given
            val timestamp = Calendar.getInstance().time
            val nestedObjectMap = mapOf("1" to 3)
            val deserializedMap = mapOf("timestamp" to timestamp.time, "object" to nestedObjectMap)
            every { mockSerializer.deserialize(deserializingValue) } returns deserializedMap

            every { mockMapper.fromMap(nestedObjectMap) } throws IllegalStateException()

            // when
            val e = assertThatQonversionExceptionThrown(ErrorCode.Deserialization) {
                cacheMapper.fromSerializedString(deserializingValue)
            }

            // then
            assertThat(e.message).isEqualTo("Mapper had thrown the exception")
            verify(exactly = 1) {
                mockSerializer.deserialize(deserializingValue)
                mockMapper.fromMap(nestedObjectMap)
            }
        }
    }
}
