package com.qonversion.android.sdk.storage

import com.qonversion.android.sdk.QonversionConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPropertiesStorageTest {
    private val mockSharedPreferencesCache = mockk<SharedPreferencesCache>(relaxed = true)
    private val mockConfig = mockk<QonversionConfig>(relaxed = true)
    private lateinit var userPropertiesStorage: UserPropertiesStorage

    @Before
    fun setUp() {
        userPropertiesStorage = UserPropertiesStorage(mockSharedPreferencesCache, mockConfig)
    }

    @Test
    fun saveProperties() {
        userPropertiesStorage.save("any_string_key_1", "any_string_value_1")
        userPropertiesStorage.save("any_string_key_2", "any_string_value_2")
        userPropertiesStorage.save("any_string_key_1", "new_string_value_1")
        val properties: Map<String, String> = userPropertiesStorage.getProperties()

        Assert.assertTrue(properties.isNotEmpty())
        Assert.assertTrue(properties.containsKey("any_string_key_1"))
        Assert.assertTrue(properties.containsKey("any_string_key_2"))
        Assert.assertEquals(properties.getValue("any_string_key_1"), "new_string_value_1")
        Assert.assertEquals(properties.getValue("any_string_key_2"), "any_string_value_2")
    }

    @Test
    fun saveAndClearProperties() {
        userPropertiesStorage.save("any_string_key_1", "any_string_value_1")
        userPropertiesStorage.save("any_string_key_2", "any_string_value_2")

        userPropertiesStorage.clear(mapOf("any_string_key_1" to "any_string_value_1",
        "any_string_key_2" to "any_string_value_2"))
        Assert.assertTrue(userPropertiesStorage.getProperties().isEmpty())
    }

    @Test
    fun `get handled properties when storage contains valid value`() {
        // given
        val properties = mapOf("someKey" to "someValue")
        val jsonString: String = JSONObject(properties).toString()

        every {
            mockSharedPreferencesCache.getString("com.qonversion.keys.handled_properties_key", defValue = any())
        } returns jsonString

        // when
        val handledProperties = userPropertiesStorage.getHandledProperties()

        // then
        assert(handledProperties == properties)
    }

    @Test
    fun `get handled properties when storage contains not map`() {
        // given
        val jsonString: String = JSONArray().toString()

        every {
            mockSharedPreferencesCache.getString("com.qonversion.keys.handled_properties_key", defValue = any())
        } returns jsonString

        // when
        val handledProperties = userPropertiesStorage.getHandledProperties()

        // then
        assert(handledProperties.isEmpty())
    }

    @Test
    fun `get handled properties when storage is empty`() {
        // given
        every {
            mockSharedPreferencesCache.getString("com.qonversion.keys.handled_properties_key", defValue = null)
        } returns null

        // when
        val handledProperties = userPropertiesStorage.getHandledProperties()

        // then
        assert(handledProperties.isEmpty())
    }

    @Test
    fun saveHandledProperties() {
        //  given
        val properties = mapOf("someKey" to "someValue")
        val jsonString: String = JSONObject(properties).toString()

        // when
        userPropertiesStorage.saveHandledProperties(properties)

        // then
        verify {
            mockSharedPreferencesCache.putString("com.qonversion.keys.handled_properties_key", jsonString)
        }
    }
}
