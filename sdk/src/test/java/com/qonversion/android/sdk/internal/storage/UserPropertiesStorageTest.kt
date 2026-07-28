package com.qonversion.android.sdk.internal.storage

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class UserPropertiesStorageTest {
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
    fun clearIsValueConditional() {
        // A key overwritten while its previous value was in flight must survive
        // clearing by the sent (old) snapshot — otherwise the new value is lost.
        userPropertiesStorage.save("email", "old@x.com")
        userPropertiesStorage.save("name", "unchanged")

        userPropertiesStorage.save("email", "new@x.com")
        userPropertiesStorage.clear(mapOf("email" to "old@x.com", "name" to "unchanged"))

        Assert.assertEquals(mapOf("email" to "new@x.com"), userPropertiesStorage.getProperties())
    }
}
