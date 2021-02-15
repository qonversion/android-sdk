package com.qonversion.android.sdk.storage

import android.content.SharedPreferences
import com.qonversion.android.sdk.dto.QLaunchResult
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SharedPreferencesCacheTest {
    private val mockPrefs: SharedPreferences = mockk(relaxed = true)
    private val mockEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private lateinit var prefsCache: SharedPreferencesCache

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockSharedPreferences()

        prefsCache = SharedPreferencesCache(mockPrefs)
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

            prefsCache.putInt(key, value)

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

            val expectedValue = prefsCache.getInt(key)

            verify(exactly = 1) {
                mockPrefs.getInt(key, 0)
            }
            assertThat(value).isEqualTo(expectedValue)
        }

        @Test
        fun `should load Int preference with key and defValue when it doesn't exist`() {
            val key = "key"

            val expectedValue = prefsCache.getInt(key)
            verify(exactly = 1) {
                mockPrefs.getInt(key, 0)
            }
            assertThat(expectedValue).isEqualTo(0)
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

            prefsCache.putLong(key, value)

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

            val expectedValue = prefsCache.getLong(key)

            verify(exactly = 1) {
                mockPrefs.getLong(key, 0)
            }
            assertThat(value).isEqualTo(expectedValue)
        }

        @Test
        fun `should load Long preference with key and defValue when it doesn't exist`() {
            val key = "key"

            val expectedValue = prefsCache.getLong(key)
            verify(exactly = 1) {
                mockPrefs.getLong(key, 0)
            }
            assertThat(expectedValue).isEqualTo(0)
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

            prefsCache.putFloat(key, value)

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

            val expectedValue = prefsCache.getFloat(key)

            verify(exactly = 1) {
                mockPrefs.getFloat(key, 0F)
            }
            assertThat(value).isEqualTo(expectedValue)
        }

        @Test
        fun `should load Float preference with key and defValue when it doesn't exist`() {
            val key = "key"

            val expectedValue = prefsCache.getFloat(key)
            verify(exactly = 1) {
                mockPrefs.getFloat(key, 0F)
            }
            assertThat(expectedValue).isEqualTo(0F)
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

            prefsCache.putString(key, value)

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

            val expectedValue = prefsCache.getString(key)

            verify(exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(value).isEqualTo(expectedValue)
        }

        @Test
        fun `should load String preference with key and defValue when it doesn't exist`() {
            val key = "key"

            val expectedValue = prefsCache.getString(key)
            verify(exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(expectedValue).isEqualTo("")
        }
    }

    @Nested
    inner class Object {
        @Test
        fun `should save Object preference with key`() {
            val key = "launchResultKey"
            val value = Util.LAUNCH_RESULT
            val valueJsonStr = Util.LAUNCH_RESULT_JSON_STR
            val mockMoshi = Util.buildMoshi()
            val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)

            every {
                mockEditor.putString(key, valueJsonStr)
            } returns mockEditor

            prefsCache.putObject(key, value, mockAdapter)

            verifyOrder {
                mockEditor.putString(key, valueJsonStr)
                mockEditor.apply()
            }
        }

        @Test
        fun `should load Object preference with key when it exists`() {
            val key = "launchResultKey"
            val expectedValue = Util.LAUNCH_RESULT
            val valueJsonStr = Util.LAUNCH_RESULT_JSON_STR
            val mockMoshi = Util.buildMoshi()
            val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)

            every {
                mockPrefs.getString(key, "")
            } returns valueJsonStr

            val realValue = prefsCache.getObject(key, mockAdapter)

            verify (exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(realValue).isEqualTo(expectedValue)
        }

        @Test
        fun `should not load Object preference with key when it doesn't exist`() {
            val key = "launchResultKey"
            val expectedValue = null
            val mockMoshi = Util.buildMoshi()
            val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)

            every {
                mockPrefs.getString(key, "")
            } returns null

            val actualValue = prefsCache.getObject(key, mockAdapter)

            verify (exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(actualValue).isEqualTo(expectedValue)
        }

        @Test
        fun `should return null when sharedPreferences returns invalid json string for Object with key`() {
            val key = "launchResultKey"
            val expectedValue = null
            val mockMoshi = Util.buildMoshi()
            val mockAdapter = mockMoshi.adapter(QLaunchResult::class.java)
            val invalidValueJsonStr = "Invalid Object Json Str"


            every {
                mockPrefs.getString(key, "")
            } returns invalidValueJsonStr

            val actualValue = prefsCache.getObject(key, mockAdapter)

            verify (exactly = 1) {
                mockPrefs.getString(key, "")
            }
            assertThat(actualValue).isEqualTo(expectedValue)
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
