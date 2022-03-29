package com.qonversion.android.sdk.internal.common.mappers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

internal class ExtensionsTest {

    private val correctKey = "there is a real expected value!"
    private val incorrectKey = "there is a value with unexpected type!"
    private val emptyKey = "there is no value!"

    @Test
    fun `get map`() {
        // given
        val correctResult = emptyMap<Any, Any>()
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to "just a string"), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getMap(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get list`() {
        // given
        val correctResult = emptyList<Any>()
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to "just a string"), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getList(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get boolean`() {
        // given
        val correctResult = true
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to "just a string"), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getBoolean(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get int`() {
        // given
        val correctResult = 42
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to "just a string"), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getInt(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get string`() {
        // given
        val correctResult = "Yeah, I'm real"
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to 42), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getString(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get float`() {
        // given
        val correctResult = 42f
        // key to (given map to expected result)
        val testCases = mapOf(
            correctKey to Pair(mapOf(correctKey to correctResult), correctResult),
            incorrectKey to Pair(mapOf(incorrectKey to "just a string"), null),
            emptyKey to Pair(mapOf(), null)
        )

        testCases.forEach { (key, testCase) ->
            // when
            val res = testCase.first.getFloat(key)

            // then
            assertThat(res).isEqualTo(testCase.second)
        }
    }

    @Test
    fun `get correct date`() {
        // given
        val correctResult = 1111L
        val map = mapOf(correctKey to correctResult)

        // when
        val res = map.getDate(correctKey)

        // then
        assertThat(res).isNotNull
        assertThat(res).isInstanceOf(Date::class.java)
    }

    @Test
    fun `get incorrect date`() {
        // given
        val map = mapOf(incorrectKey to "just a string")

        // when
        val res = map.getDate(incorrectKey)

        // then
        assertThat(res).isNull()
    }

    @Test
    fun `get empty date`() {
        // given
        val map = emptyMap<Any, Any>()

        // when
        val res = map.getDate(emptyKey)

        // then
        assertThat(res).isNull()
    }
}