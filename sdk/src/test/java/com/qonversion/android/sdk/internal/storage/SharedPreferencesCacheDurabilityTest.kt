package com.qonversion.android.sdk.internal.storage

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SharedPreferencesCacheDurabilityTest {
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val cache = SharedPreferencesCache(preferences)

    @Test
    fun `batch string update is durably committed as one transaction`() {
        every { preferences.contains(any()) } returns false
        every { preferences.edit() } returns editor
        every { editor.remove("stale") } returns editor
        every { editor.putString("current", "payload") } returns editor
        every { editor.commit() } returns true

        val committed = cache.updateStringsDurably(
            values = mapOf("current" to "payload"),
            removedKeys = setOf("stale"),
        )

        assertTrue(committed)
        verifyOrder {
            editor.remove("stale")
            editor.putString("current", "payload")
            editor.commit()
        }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `batch string update exposes a failed disk commit`() {
        val rollbackEditor = mockk<SharedPreferences.Editor>()
        every { preferences.contains("current") } returns true
        every { preferences.getString("current", null) } returns "previous"
        every { preferences.edit() } returnsMany listOf(editor, rollbackEditor)
        every { editor.putString("current", "payload") } returns editor
        every { editor.commit() } returns false
        every { rollbackEditor.putString("current", "previous") } returns rollbackEditor
        every { rollbackEditor.apply() } returns Unit

        val committed = cache.updateStringsDurably(
            values = mapOf("current" to "payload"),
            removedKeys = emptySet(),
        )

        assertFalse(committed)
        verifyOrder {
            editor.putString("current", "payload")
            editor.commit()
            rollbackEditor.putString("current", "previous")
            rollbackEditor.apply()
        }
    }

    @Test
    fun `batch string update restores the prior in-process view when commit throws`() {
        val rollbackEditor = mockk<SharedPreferences.Editor>()
        every { preferences.contains("current") } returns false
        every { preferences.edit() } returnsMany listOf(editor, rollbackEditor)
        every { editor.putString("current", "payload") } returns editor
        every { editor.commit() } throws IllegalStateException("disk unavailable")
        every { rollbackEditor.remove("current") } returns rollbackEditor
        every { rollbackEditor.apply() } returns Unit

        assertThrows(IllegalStateException::class.java) {
            cache.updateStringsDurably(
                values = mapOf("current" to "payload"),
                removedKeys = emptySet(),
            )
        }

        verifyOrder {
            editor.putString("current", "payload")
            editor.commit()
            rollbackEditor.remove("current")
            rollbackEditor.apply()
        }
    }
}
