package com.qonversion.android.sdk.internal.api

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ApiHelperTest {

    private lateinit var apiHelper: ApiHelper

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        apiHelper = ApiHelper()
    }

    @Nested
    inner class IsV1Request {
        @Test
        fun `should return true when request version is 1`() {
            // given
            val url = "https://api.qonversion.io/v1/user/init"
            val request = mockRequestWithUrl(url)

            // when
            val result = apiHelper.isV1Request(request)

            // then
            Assertions.assertThat(result).isTrue
        }

        @Test
        fun `should return false when request version is 2`() {
            // given
            val url = "https://api.qonversion.io/v2/screens/id"
            val request = mockRequestWithUrl(url)

            // when
            val result = apiHelper.isV1Request(request)

            // then
            Assertions.assertThat(result).isFalse
        }

        private fun mockRequestWithUrl(url: String): Request {
            val request = mockk<Request>()
            every {
                request.url().toString()
            } returns url

            return request
        }
    }
}