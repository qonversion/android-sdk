package com.qonversion.android.sdk.internal.localStorage

import android.content.SharedPreferences
import com.qonversion.android.sdk.internal.common.localStorage.SharedPreferencesStorage
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SharedPreferencesStorageTest {
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var prefsStorage: SharedPreferencesStorage

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockSharedPreferences()

        prefsStorage = SharedPreferencesStorage(mockPrefs)
    }

    @Nested
    inner class Int {
        @Test
        fun `should save Int preference with key`() {
            val key = "key"
            val value = 57

            every {
                mockEditor.putInt(key, value)
            } returns mockEditor

            prefsStorage.putInt(key, value)

            verifyOrder {
                mockEditor.putInt(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Int preference with key when it exists`() {
            val key = "key"
            val value = 57

            every {
                mockPrefs.getInt(key, any())
            } returns value

            val expectedValue = prefsStorage.getInt(key, 0)

            verify(exactly = 1) {
                mockPrefs.getInt(key, 0)
            }
            Assertions.assertThat(value).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class Long {
        @Test
        fun `should save Long preference with key`() {
            val key = "key"
            val value = 57L

            every {
                mockEditor.putLong(key, value)
            } returns mockEditor

            prefsStorage.putLong(key, value)

            verifyOrder {
                mockEditor.putLong(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Long preference with key when it exists`() {
            val key = "key"
            val value = 57L

            every {
                mockPrefs.getLong(key, any())
            } returns value

            val expectedValue = prefsStorage.getLong(key, 0)

            verify(exactly = 1) {
                mockPrefs.getLong(key, 0)
            }
            Assertions.assertThat(value).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class Float {
        @Test
        fun `should save Float preference with key`() {
            val key = "key"
            val value = 57F

            every {
                mockEditor.putFloat(key, value)
            } returns mockEditor

            prefsStorage.putFloat(key, value)

            verifyOrder {
                mockEditor.putFloat(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Float preference with key when it exists`() {
            val key = "key"
            val value = 57F

            every {
                mockPrefs.getFloat(key, any())
            } returns value

            val expectedValue = prefsStorage.getFloat(key, 0F)

            verify(exactly = 1) {
                mockPrefs.getFloat(key, 0F)
            }
            Assertions.assertThat(value).isEqualTo(expectedValue)
        }
    }

    @Nested
    inner class String {
        @Test
        fun `should save String preference with key`() {
            val key = "key"
            val value = "57"

            every {
                mockEditor.putString(key, value)
            } returns mockEditor

            prefsStorage.putString(key, value)

            verifyOrder {
                mockEditor.putString(key, value)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load String preference with key when it exists`() {
            val key = "key"
            val value = "57"

            every {
                mockPrefs.getString(key, any())
            } returns value

            val expectedValue = prefsStorage.getString(key, "")

            verify(exactly = 1) {
                mockPrefs.getString(key, "")
            }
            Assertions.assertThat(value).isEqualTo(expectedValue)
        }
    }

    private fun mockSharedPreferences() {
        every {
            mockPrefs.edit()
        } returns mockEditor

        every {
            mockEditor.apply()
        } just runs
    }
}