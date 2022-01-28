package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.MapDataMapper
import com.qonversion.android.sdk.internal.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested

import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

internal class UserPropertiesStorageImplTest {

    private lateinit var userPropertiesStorage: UserPropertiesStorageImpl

    private val mockLocalStorage = mockk<LocalStorage>()
    private val mockMapper = mockk<MapDataMapper>()
    private val propertiesKeyInMemory = "com.qonversion.keys.userProperties"
    private val propertiesStr = "properties"
    private val mockLogger = mockk<Logger>()
    private val slotErrorLogMessage = slot<String>()

    @BeforeEach
    fun setUp() {
        every { mockLogger.error(capture(slotErrorLogMessage), any()) } just runs

        userPropertiesStorage =
            UserPropertiesStorageImpl(mockLocalStorage, mockMapper, mockLogger)
    }

    @Nested
    inner class GetProperties {
        @Test
        fun `get filled properties`() {
            // given
            val expectedProperties = mapOf("key1" to "value1", "key2" to "value2")
            mockGetPropertiesFromStorage(expectedProperties)

            // when
            val result = userPropertiesStorage.properties

            // then
            verifyGetPropertiesFromStorage()
            assertThat(result).isEqualTo(expectedProperties)
        }

        @Test
        fun `get empty properties`() {
            // given
            val expectedProperties = emptyMap<String, String>()
            mockGetPropertiesFromStorage(expectedProperties)

            // when
            val result = userPropertiesStorage.properties

            // then
            verifyGetPropertiesFromStorage()
            assertThat(result).isEqualTo(expectedProperties)
        }
    }

    @Nested
    inner class SetProperty {
        private lateinit var spykStorage: UserPropertiesStorageImpl

        @BeforeEach
        fun setUp() {
            spykStorage = spyk(userPropertiesStorage)

            every {
                spykStorage.putPropertiesToStorage()
            } just runs
        }

        @Test
        fun `set property when properties val is empty`() {
            // given
            val existingProperties = mutableMapOf<String, String>()

            val key = "key"
            val value = "value"
            val expectedProperties = mapOf(key to value)

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(key, value)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(expectedProperties)
        }

        @Test
        fun `set property when properties val is not empty`() {
            // given
            val key = "key"
            val value = "value"
            val newProperties = mapOf(key to value)
            val existingProperties = mutableMapOf("oldKey" to "oldValue")

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(key, value)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(newProperties + existingProperties)
        }

        @Test
        fun `update property`() {
            // given
            val key = "key"
            val newValue = "newValue"
            val expectedProperties = mapOf(key to newValue)
            val existingProperties = mutableMapOf(key to "oldValue")

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(key, newValue)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(expectedProperties)
        }
    }

    @Nested
    inner class SetProperties {
        private lateinit var spykStorage: UserPropertiesStorageImpl

        @BeforeEach
        fun setUp() {
            spykStorage = spyk(userPropertiesStorage)

            every {
                spykStorage.putPropertiesToStorage()
            } just runs
        }

        @Test
        fun `set empty properties`() {
            // given
            val existingProperties = mutableMapOf<String, String>()

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(emptyMap())

            // then
            verify(exactly = 0) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(existingProperties)
        }

        @Test
        fun `set properties when properties val is empty`() {
            // given
            val properties = mapOf("key1" to "value1", "key2" to "value2")
            val existingProperties = mutableMapOf<String, String>()

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(properties)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(properties)
        }

        @Test
        fun `set properties when properties val is not empty`() {
            // given
            val properties = mapOf("key1" to "value1", "key2" to "value2")
            val existingProperties = mutableMapOf("oldKey" to "oldValue")

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(properties)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(properties + existingProperties)
        }

        @Test
        fun `update properties`() {
            // given
            val newProperties = mapOf("key1" to "newValue1", "key2" to "newValue1")
            val existingProperties =
                mapOf("key1" to "oldValue1", "key2" to "oldValue2", "key3" to "value3")
            val expectedProperties =
                mapOf("key1" to "newValue1", "key2" to "newValue1", "key3" to "value3")

            mockGetPropertiesFromStorage(existingProperties)

            // when
            spykStorage.set(newProperties)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(expectedProperties)
        }
    }

    @Nested
    inner class Delete {
        private lateinit var spykStorage: UserPropertiesStorageImpl

        @BeforeEach
        fun setUp() {
            spykStorage = spyk(userPropertiesStorage)

            every {
                spykStorage.putPropertiesToStorage()
            } just runs
        }

        @Test
        fun `delete non-existent property`() {
            // given
            val key = "keyToDelete"

            val existingProperties = mutableMapOf("key" to "value")

            every {
                spykStorage.properties
            } returns existingProperties

            // when
            spykStorage.delete(key)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(existingProperties)
        }

        @Test
        fun `delete existing property`() {
            // given
            val key = "keyToDelete"

            val existingProperties = mutableMapOf("keyToDelete" to "value")

            every {
                spykStorage.properties
            } returns existingProperties

            // when
            spykStorage.delete(key)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEmpty()
        }
    }

    @Nested
    inner class DeleteProperties {
        private lateinit var spykStorage: UserPropertiesStorageImpl

        @BeforeEach
        fun setUp() {
            spykStorage = spyk(userPropertiesStorage)

            every {
                spykStorage.putPropertiesToStorage()
            } just runs
        }

        @Test
        fun `delete non-existent properties`() {
            // given
            val keys = listOf("keyToDelete1", "keyToDelete2")

            val existingProperties = mutableMapOf("key" to "value")

            every {
                spykStorage.properties
            } returns existingProperties

            // when
            spykStorage.delete(keys)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(existingProperties)
        }

        @Test
        fun `delete existent properties`() {
            // given
            val keys = listOf("keyToDelete1", "keyToDelete2")
            val expectedProperties = mapOf("key3" to "value3")
            val existingProperties = expectedProperties + (
                    mapOf(
                        "keyToDelete1" to "value1",
                        "keyToDelete2" to "value2"
                    ))

            every {
                spykStorage.properties
            } returns existingProperties.toMutableMap()

            // when
            spykStorage.delete(keys)

            // then
            verify(exactly = 1) {
                spykStorage.putPropertiesToStorage()
            }
            val updatedProperties = spykStorage.properties
            assertThat(updatedProperties).isEqualTo(expectedProperties)
        }
    }

    @Nested
    inner class GetPropertiesFromStorage {
        @Test
        fun `get properties from filled storage`() {
            // given
            val jsonString = "jsonString"
            val properties = mapOf("key1" to "value1", "key2" to "value2")
            every {
                mockLocalStorage.getString(propertiesKeyInMemory)
            } returns jsonString

            every {
                mockMapper.toMap(jsonString)
            } returns properties

            // when
            val result = userPropertiesStorage.getPropertiesFromStorage()

            //then
            assertThat(result).isEqualTo(result)
            verifySequence {
                mockLocalStorage.getString(propertiesKeyInMemory)
                mockMapper.toMap(jsonString)
            }
        }

        @Test
        fun `get properties from empty storage`() {
            // given
            every {
                mockLocalStorage.getString(propertiesKeyInMemory)
            } returns null

            // when
            val result = userPropertiesStorage.getPropertiesFromStorage()

            //then
            verify { mockMapper wasNot called }
            assertThat(result).isEmpty()
        }

        @Test
        fun `get properties when exception occurred`() {
            // given
            val errorString = "Couldn't load properties from storage"
            val jsonString = "jsonString"
            every {
                mockLocalStorage.getString(propertiesKeyInMemory)
            } returns jsonString

            val exception = IllegalStateException("Couldn't create JSONObject from string")
            every {
                mockMapper.toMap(jsonString)
            } throws exception

            // when
            val result = userPropertiesStorage.getPropertiesFromStorage()
            assertThat(result).isEmpty()

            // then
            assertThat(slotErrorLogMessage.captured)
                .startsWith(errorString)
            verify(exactly = 1) {
                mockLogger.error(errorString, exception)
            }
        }
    }

    @Nested
    inner class PutPropertiesToStorage {
        @Test
        fun `put properties from filled storage`() {
            // given
            val jsonString = "jsonString"
            val existingProperties = mapOf("key1" to "value1", "key2" to "value2")

            mockGetPropertiesFromStorage(existingProperties)

            every {
                mockMapper.fromMap(existingProperties)
            } returns jsonString

            every {
                mockLocalStorage.putString(propertiesKeyInMemory, jsonString)
            } just runs

            // when
            userPropertiesStorage.putPropertiesToStorage()

            // then
            verify(exactly = 1) {
                mockLocalStorage.putString(propertiesKeyInMemory, jsonString)
            }
        }

        @Test
        fun `put properties when exception occurred`() {
            // given
            val errorString = "Couldn't save properties to storage"
            val existingProperties = mapOf("key1" to "value1", "key2" to "value2")
            mockGetPropertiesFromStorage(existingProperties)

            val exception = IllegalStateException("Couldn't create JSONObject from map")
            every {
                mockMapper.fromMap(existingProperties)
            } throws exception

            // when
            userPropertiesStorage.putPropertiesToStorage()

            // then
            verify(exactly = 0) {
                mockLocalStorage.putString(any(), any())
            }
            assertThat(slotErrorLogMessage.captured)
                .startsWith(errorString)
            verify(exactly = 1) {
                mockLogger.error(errorString, exception)
            }
        }
    }

    private fun mockGetPropertiesFromStorage(properties: Map<String, String>) {
        // Due to https://github.com/mockk/mockk/issues/468
        // There is no chance to spyk code block inside 'by lazy'
        every {
            mockLocalStorage.getString(propertiesKeyInMemory)
        } returns propertiesStr

        every {
            mockMapper.toMap(propertiesStr)
        } returns properties
    }

    private fun verifyGetPropertiesFromStorage() {
        verifySequence {
            mockLocalStorage.getString(propertiesKeyInMemory)
            mockMapper.toMap(propertiesStr)
        }
    }
}
