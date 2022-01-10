package com.qonversion.android.sdk.internal.networkLayer.requestConfigurator

import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.headerBuilder.HeaderBuilder
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

internal class RequestConfiguratorImplTest {

    private lateinit var requestConfigurator: RequestConfigurator

    private val headerBuilder = mockk<HeaderBuilder>()
    private val testBaseUrl = "test.io"
    private val testUserId = "testId"
    private val testCommonHeaders = mapOf("someKey" to "someVal")

    @Before
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
}
