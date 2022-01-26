package com.qonversion.android.sdk.internal.common.mappers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test

internal class UserPropertiesMapperTest {
    private lateinit var mapper: UserPropertiesMapper
    private val defaultDataMap = mapOf(
        "result" to "ok",
        "error" to "",
        "errors" to listOf<String>()
    )

    @BeforeEach
    fun setUp() {
        mapper = UserPropertiesMapper()
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
    fun `get empty processed properties`() {
        // given
        val expectedProperties = listOf<String>()
        val data = defaultDataMap + mapOf(
            "processed" to expectedProperties
        )

        // when
        val properties = mapper.fromMap(data)

        // then
        assertThat(properties).isEqualTo(expectedProperties)
    }

    @Test
    fun `get null processed properties`() {
        // given
        val expectedProperties = emptyList<String>()
        val data = defaultDataMap + mapOf(
            "processed" to null
        )

        // when
        val properties = mapper.fromMap(data)

        // then
        assertThat(properties).isEqualTo(expectedProperties)
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
