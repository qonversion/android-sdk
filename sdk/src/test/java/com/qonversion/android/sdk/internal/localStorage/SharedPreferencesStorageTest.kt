package com.qonversion.android.sdk.internal.localStorage

import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SharedPreferencesStorageTest {
    private val mockPrefs: SharedPreferences = mockk()
    private val mockEditor: SharedPreferences.Editor = mockk()

    private lateinit var prefsStorage: SharedPreferencesStorage

    @BeforeEach
    fun setUp() {
        mockSharedPreferences()

        prefsStorage = SharedPreferencesStorage(mockPrefs)
    }

    @Nested
    inner class Int {
        @Test
        fun `should save Int preference with key`() {
            // given
            val key = "key"
            val value = 57

            every {
                mockEditor.putInt(key, value)
            } returns mockEditor

            // when
            prefsStorage.putInt(key, value)

            // then
            verifyOrder {
                mockPrefs.edit()
                mockEditor.putInt(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Int preference with key when it exists`() {
            // given
            val key = "key"
            val expectedValue = 57

            every {
                mockPrefs.getInt(key, any())
            } returns expectedValue

            // when
            val result = prefsStorage.getInt(key, 0)

            // then
            verify(exactly = 1) {
                mockPrefs.getInt(key, 0)
            }
            assertThat(result).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class Long {
        @Test
        fun `should save Long preference with key`() {
            // given
            val key = "key"
            val value = 57L

            every {
                mockEditor.putLong(key, value)
            } returns mockEditor

            // when
            prefsStorage.putLong(key, value)

            // then
            verifyOrder {
                mockPrefs.edit()
                mockEditor.putLong(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Long preference with key when it exists`() {
            // given
            val key = "key"
            val expectedValue = 57L

            every {
                mockPrefs.getLong(key, any())
            } returns expectedValue

            // when
            val result = prefsStorage.getLong(key, 0)

            // then
            verify(exactly = 1) {
                mockPrefs.getLong(key, 0)
            }
            assertThat(result).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class Float {
        @Test
        fun `should save Float preference with key`() {
            // given
            val key = "key"
            val value = 57F

            every {
                mockEditor.putFloat(key, value)
            } returns mockEditor

            // when
            prefsStorage.putFloat(key, value)

            // then
            verifyOrder {
                mockPrefs.edit()
                mockEditor.putFloat(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Float preference with key when it exists`() {
            // given
            val key = "key"
            val expectedValue = 57F

            every {
                mockPrefs.getFloat(key, any())
            } returns expectedValue

            // when
            val result = prefsStorage.getFloat(key, 0F)

            // then
            verify(exactly = 1) {
                mockPrefs.getFloat(key, 0F)
            }
            assertThat(result).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class String {
        @Test
        fun `should save String preference with key`() {
            // given
            val key = "key"
            val value = "57"

            every {
                mockEditor.putString(key, value)
            } returns mockEditor

            // when
            prefsStorage.putString(key, value)

            // then
            verifyOrder {
                mockPrefs.edit()
                mockEditor.putString(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load String preference with key when it exists`() {
            // given
            val key = "key"
            val expectedValue = "57"

            every {
                mockPrefs.getString(key, any())
            } returns expectedValue

            // when
            val result = prefsStorage.getString(key, "")

            // then
            verify(exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(result).isEqualTo(expectedValue)
        }

        @Test
        fun `should load String preference with key without default value`() {
            // given
            val key = "key"
            val expectedValue = "57"

            every { mockPrefs.getString(key, null) } returns expectedValue

            // when
            val result = prefsStorage.getString(key)

            // then
            verify(exactly = 1) { mockPrefs.getString(key, null) }
            assertThat(result).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class RemoveObject {
        @Test
        fun `should remove preferences with key`() {
            // given
            val key = "key"

            // when
            prefsStorage.remove(key)

            // then
            verifyOrder {
                mockPrefs.edit()
                mockEditor.remove(key)
                mockEditor.apply()
            }
        }
    }

    private fun mockSharedPreferences() {
        every {
            mockPrefs.edit()
        } returns mockEditor

        every {
            mockEditor.remove(any())
        } returns mockEditor

        every {
            mockEditor.apply()
        } just runs
    }
}
