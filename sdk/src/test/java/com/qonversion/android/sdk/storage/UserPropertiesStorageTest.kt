package com.qonversion.android.sdk.storage

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPropertiesStorageTest {
    private lateinit var userPropertiesStorage: UserPropertiesStorage

    @Before
    fun setUp() {
        userPropertiesStorage = UserPropertiesStorage()
    }

    @Test
    fun saveProperties() {
        userPropertiesStorage.save("any_string_key_1", "any_string_value_1")
        userPropertiesStorage.save("any_string_key_2", "any_string_value_2")
        userPropertiesStorage.save("any_string_key_1", "new_string_value_1")
        val properties:MutableMap<String, String> = userPropertiesStorage.getProperties()

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

        userPropertiesStorage.clear()
        Assert.assertTrue(userPropertiesStorage.getProperties().isEmpty())
    }
}
