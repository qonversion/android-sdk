package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QScreenVariable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenMapperTest {

    private val mapper = ScreenMapper()

    private fun baseScreen(vararg extra: Pair<String, Any?>): Map<String, Any?> =
        mapOf("id" to "s1", "body" to "<html>", "context_key" to "ctx", *extra)

    @Test
    fun `decodes configured products list`() {
        val screen = mapper.fromMap(baseScreen("products" to listOf("annual", "weekly")))
        assertEquals(listOf("annual", "weekly"), screen?.products)
    }

    @Test
    fun `missing products defaults to empty list`() {
        val screen = mapper.fromMap(baseScreen())
        assertEquals(emptyList<String>(), screen?.products)
    }

    @Test
    fun `empty products stays empty`() {
        val screen = mapper.fromMap(baseScreen("products" to emptyList<String>()))
        assertEquals(emptyList<String>(), screen?.products)
    }

    @Test
    fun `non-string product entries are dropped`() {
        val screen = mapper.fromMap(baseScreen("products" to listOf("a", 1, null, "b")))
        assertEquals(listOf("a", "b"), screen?.products)
    }

    @Test
    fun `returns null when a required field is missing`() {
        assertNull(mapper.fromMap(mapOf("id" to "s1", "body" to "<html>")))
    }

    // DEV-1170 — authored screen variables, native types preserved by key.
    @Test
    fun `decodes typed variables preserving native types`() {
        val screen = mapper.fromMap(
            baseScreen(
                "variables" to listOf(
                    mapOf("key" to "isTrial", "type" to "boolean", "value" to true),
                    mapOf("key" to "headline", "type" to "string", "value" to "Go Premium"),
                    mapOf("key" to "discount", "type" to "number", "value" to 30.0),
                ),
            ),
        )
        assertEquals(
            listOf(
                QScreenVariable("isTrial", "boolean", true),
                QScreenVariable("headline", "string", "Go Premium"),
                QScreenVariable("discount", "number", 30.0),
            ),
            screen?.variables,
        )
    }

    // Real-data edge cases (staging): space in key, empty-string default kept.
    @Test
    fun `preserves space in key and empty string default`() {
        val screen = mapper.fromMap(
            baseScreen(
                "variables" to listOf(
                    mapOf("key" to "variable name", "type" to "string", "value" to ""),
                ),
            ),
        )
        assertEquals(listOf(QScreenVariable("variable name", "string", "")), screen?.variables)
    }

    @Test
    fun `skips variable without a key and defaults missing type to string`() {
        val screen = mapper.fromMap(
            baseScreen(
                "variables" to listOf(
                    mapOf("type" to "string", "value" to "orphan"), // no key -> dropped
                    mapOf("key" to "legacy", "value" to "y"), // missing type -> "string"
                ),
            ),
        )
        assertEquals(listOf(QScreenVariable("legacy", "string", "y")), screen?.variables)
    }

    @Test
    fun `missing variables defaults to empty list`() {
        val screen = mapper.fromMap(baseScreen())
        assertEquals(emptyList<QScreenVariable>(), screen?.variables)
    }
}
