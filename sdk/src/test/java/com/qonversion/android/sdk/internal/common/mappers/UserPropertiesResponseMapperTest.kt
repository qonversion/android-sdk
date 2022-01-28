package com.qonversion.android.sdk.internal.common.mappers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class UserPropertiesResponseMapperTest {
    private lateinit var mapper: UserPropertiesResponseMapper
    private val defaultDataMap = mapOf(
        "result" to "ok",
        "error" to "",
        "errors" to listOf<String>()
    )

    @BeforeEach
    fun setUp() {
        mapper = UserPropertiesResponseMapper()
    }

    @Test
    fun `get filled processed properties`() {
        // given
        val expectedProperties = listOf("_q_email", "_q_adjust_adid")
        val data = defaultDataMap + mapOf(
            "processed" to expectedProperties
        )

        // when
        val processedProperties = mapper.fromMap(data)

        // then
        assertThat(processedProperties).isEqualTo(expectedProperties)
    }

    @Test
    fun `get empty and null processed properties`() {
        // given
        val emptyProperties = listOf<String>()
        val listProperties = listOf(
            emptyProperties, null
        )

        listProperties.forEach { properties ->
            val data = defaultDataMap + mapOf(
                "processed" to properties
            )

            // when
            val result = mapper.fromMap(data)

            // then
            assertThat(result).isEqualTo(emptyProperties)
        }
    }

    @Test
    fun `get missing processed properties`() {
        // given
        val expectedProperties = emptyList<String>()
        val data = defaultDataMap

        // when
        val properties = mapper.fromMap(data)

        // then
        assertThat(properties).isEqualTo(expectedProperties)
    }
}
