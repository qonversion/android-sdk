package io.qonversion.nocodes.internal.common.mappers

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
}
