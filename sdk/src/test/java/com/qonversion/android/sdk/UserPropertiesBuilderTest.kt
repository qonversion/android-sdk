package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.UserProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


internal class UserPropertiesBuilderTest {

    private lateinit var builder: UserPropertiesBuilder

    @BeforeEach
    fun setUp() {
        builder = UserPropertiesBuilder()
    }

    @Test
    fun `set name`() {
        // given
        val name = "test name"

        // when
        builder.setName(name)

        // then
        assertThat(builder.properties[UserProperty.Name.code]).isEqualTo(name)
    }

    @Test
    fun `set custom user id`() {
        // given
        val id = "test id"

        // when
        builder.setCustomUserId(id)

        // then
        assertThat(builder.properties[UserProperty.CustomUserId.code]).isEqualTo(id)
    }

    @Test
    fun `set email`() {
        // given
        val email = "test email"

        // when
        builder.setEmail(email)

        // then
        assertThat(builder.properties[UserProperty.Email.code]).isEqualTo(email)
    }

    @Test
    fun `set Kochava device id`() {
        // given
        val deviceId = "test device id"

        // when
        builder.setKochavaDeviceId(deviceId)

        // then
        assertThat(builder.properties[UserProperty.KochavaDeviceId.code]).isEqualTo(deviceId)
    }

    @Test
    fun `set AppsFlyer user id`() {
        // given
        val userId = "test user id"

        // when
        builder.setAppsFlyerUserId(userId)

        // then
        assertThat(builder.properties[UserProperty.AppsFlyerUserId.code]).isEqualTo(userId)
    }

    @Test
    fun `set Adjust advertising id`() {
        // given
        val adId = "test advertising id"

        // when
        builder.setAdjustAdvertisingId(adId)

        // then
        assertThat(builder.properties[UserProperty.AdjustAdId.code]).isEqualTo(adId)
    }

    @Test
    fun `set Facebook attribution`() {
        // given
        val attribution = "test Facebook attribution"

        // when
        builder.setFacebookAttribution(attribution)

        // then
        assertThat(builder.properties[UserProperty.FacebookAttribution.code]).isEqualTo(attribution)
    }

    @Test
    fun `set custom user property`() {
        // given
        val key = "test key"
        val value = "test value"

        // when
        builder.setCustomUserProperty(key, value)

        // then
        assertThat(builder.properties[key]).isEqualTo(value)
    }

    @Test
    fun `build without any properties`() {
        // given
        val exp = emptyMap<String, String>()

        // when
        val res = builder.build()

        // then
        assertThat(res).isEqualTo(exp)
    }

    @Test
    fun `build normal way`() {
        // given
        val exp = mapOf(
            "one" to "three",
            "to be or not" to "be"
        )
        exp.forEach { (key, value) -> builder.properties[key] = value }

        // when
        val res = builder.build()

        // then
        assertThat(res).isEqualTo(exp)
    }
}
