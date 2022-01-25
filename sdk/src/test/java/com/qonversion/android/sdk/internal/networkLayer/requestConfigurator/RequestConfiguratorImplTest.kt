package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.config.PrimaryConfig
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LaunchMode
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.cache.InternalCacheLifetime
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RequestConfiguratorImplTest {

    private lateinit var requestConfigurator: RequestConfigurator

    private val headerBuilder = mockk<HeaderBuilder>()
    private val testBaseUrl = "test.io"
    private val testUserId = "testId"
    private val testCommonHeaders = mapOf("someKey" to "someVal")

    @BeforeEach
    fun setUp() {
        requestConfigurator = RequestConfiguratorImpl(headerBuilder, testBaseUrl)
        every {
            headerBuilder.buildCommonHeaders()
        } returns testCommonHeaders
    }

    @Test
    fun `user request`() {
        // given
        val expectedUrl = "$testBaseUrl/${ApiEndpoint.Users.path}/$testUserId"

        // when
        val request = requestConfigurator.configureUserRequest(testUserId)

        // then
        assertThat(request.type).isEqualTo(Request.Type.GET)
        assertThat(request.headers).containsExactlyEntriesOf(testCommonHeaders)
        assertThat(request.body).isNull()
        assertThat(request.url).isEqualTo(expectedUrl)
    }

    @Test
    fun `create user request`() {
        // given
        val expectedUrl = "$testBaseUrl/${ApiEndpoint.Users.path}"
        val expectedBody = mapOf("id" to testUserId)

        // when
        val request = requestConfigurator.configureCreateUserRequest(testUserId)

        // then
        assertThat(request.type).isEqualTo(Request.Type.POST)
        assertThat(request.headers).containsExactlyEntriesOf(testCommonHeaders)
        assertThat(request.body).containsExactlyEntriesOf(expectedBody)
        assertThat(request.url).isEqualTo(expectedUrl)
    }

    @Test
    fun `user properties request`() {
        // given
        val projectKey = "projectKey"
        val uid = "uid"
        val propertiesMap = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )
        val expectedBody = mapOf(
            "access_token" to projectKey,
            "q_uid" to uid,
            "properties" to propertiesMap
        )
        val expectedUrl = "$testBaseUrl/properties"

        val mockPrimaryConfig = PrimaryConfig(projectKey, mockk<LaunchMode>(), mockk<Environment>())
        mockkObject(InternalConfig)
        every { InternalConfig.uid } returns "uid"
        every { InternalConfig.primaryConfig } returns mockPrimaryConfig

        // when
        val request = requestConfigurator.configureUserPropertiesRequest(propertiesMap)

        // then
        assertThat(request.type).isEqualTo(Request.Type.POST)
        assertThat(request.headers).containsExactlyEntriesOf(testCommonHeaders)
        assertThat(request.body).containsExactlyEntriesOf(expectedBody)
        assertThat(request.url).isEqualTo(expectedUrl)
    }
}
