package com.qonversion.android.sdk.internal.common.mappers

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MapperTest {

    private val mapper = object : Mapper<Any> {
        override fun fromMap(data: Map<*, *>): Any {
            // does not testing
            return Unit
        }
    }

    @Nested
    inner class ToMapTest {

        @Test
        fun `default implementation`() {
            // given

            // when
            assertThatThrownBy {
                mapper.toMap("any value")
            }.isInstanceOf(NotImplementedError::class.java)

            // then
        }
    }

    @Nested
    inner class FromListTest {

        @Test
        fun `complete case`() {
            // given
            val spyMapper = spyk(mapper)
            val map1 = mockk<Map<Any, Any>>()
            val convertedMap1 = "converted 1"
            val map2 = mockk<Map<Any, Any>>()
            val convertedMap2 = "converted 2"
            every { spyMapper.fromMap(map1) } returns convertedMap1
            every { spyMapper.fromMap(map2) } returns convertedMap2

            // when
            val res = spyMapper.fromList(listOf(map1, map2))

            // then
            assertThat(res).isEqualTo(listOf(convertedMap1, convertedMap2))
            verify {
                spyMapper.fromMap(map1)
                spyMapper.fromMap(map2)
            }
        }

        @Test
        fun `some data is not map`() {
            // given
            val spyMapper = spyk(mapper)
            val map1 = mockk<Map<Any, Any>>()
            val convertedMap1 = "converted 1"
            every { spyMapper.fromMap(map1) } returns convertedMap1

            // when
            val res = spyMapper.fromList(listOf(map1, "string data"))

            // then
            assertThat(res).isEqualTo(listOf(convertedMap1))
            verify { spyMapper.fromMap(map1) }
        }

        @Test
        fun `only non-map data`() {
            // given

            // when
            val res = mapper.fromList(listOf(1, "string data", true, null))

            // then
            assertThat(res).isEqualTo(emptyList<Any>())
        }

        @Test
        fun `empty data`() {
            // given

            // when
            val res = mapper.fromList(emptyList<Any>())

            // then
            assertThat(res).isEqualTo(emptyList<Any>())
        }
    }
}