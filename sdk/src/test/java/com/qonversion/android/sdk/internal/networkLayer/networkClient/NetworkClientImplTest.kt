package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@ExperimentalCoroutinesApi
internal class NetworkClientImplTest {

    private lateinit var networkClient: NetworkClientImpl

    private var serializer: Serializer = mockk()

    @BeforeEach
    fun setUp() {
        networkClient = NetworkClientImpl(serializer)
    }

    @Test
    fun `parse correct url`() {
        // given
        val url = "https://qonversion.io"

        // when and then
        val parsedUrl = networkClient.parseUrl(url)

        // then
        assertThat(parsedUrl.protocol).isEqualTo("https")
        assertThat(parsedUrl.host).isEqualTo("qonversion.io")
    }

    @Test
    fun `parse incorrect url`() {
        // given
        val url = "qonversion.io"

        // when and then
        coAssertThatQonversionExceptionThrown(ErrorCode.BadNetworkRequest) {
            networkClient.parseUrl(url)
        }
    }

    @Test
    fun `connect to correct url`() {
        // given
        val url = mockk<URL>()
        val expectedConnection = mockk<HttpURLConnection>()
        every { url.openConnection() } returns expectedConnection

        // when
        val connection = networkClient.connect(url)

        // then
        assertThat(connection).isEqualTo(expectedConnection)
    }

    @Test
    fun `connect with exception`() {
        // given
        val url = mockk<URL>()
        every { url.openConnection() }.throws(IOException())

        // when and then
        assertThatQonversionExceptionThrown {
            networkClient.connect(url)
        }
    }

    @Test
    fun `prepare headers normal`() {
        // given
        val headers = mapOf("firstKey" to "firstValue", "secondKey" to "secondValue")
        val connection = prepareConnection()

        // when
        networkClient.prepareHeaders(connection, headers)

        // then
        assertThat(connection.requestProperties.map {
            it.key to it.value[0]
        }.toMap()).containsAllEntriesOf(headers)
    }

    @Test
    fun `prepare repeatable headers`() {
        // given
        val headers = mapOf("firstKey" to "firstValue", "firstKey" to "secondValue")
        val connection = prepareConnection()

        // when
        networkClient.prepareHeaders(connection, headers)

        // then
        assertThat(
            connection.requestProperties
                .map { it.key to it.value[0] }
                .toMap()
        ).containsEntry("firstKey", "secondValue")
            .doesNotContainEntry("firstKey", "firstValue")
    }

    @Test
    fun `prepare empty headers`() {
        // given
        val headers = emptyMap<String, String>()
        val connection = prepareConnection()

        // when and then
        assertDoesNotThrow {
            networkClient.prepareHeaders(connection, headers)
        }
    }

    @Test
    fun `write correct body`() {
        // given
        val body = mapOf("firstKey" to "firstValue", "firstKey" to "secondValue")
        val expectedBody = "someTestBody"
        every {
            serializer.serialize(body)
        } returns expectedBody
        val stream = ByteArrayOutputStream()

        // when
        networkClient.write(body, stream)

        // then
        assertThat(stream.toString()).isEqualTo(expectedBody)
    }

    @Test
    fun `write empty body`() {
        // given
        val body = emptyMap<String, Any?>()
        every {
            serializer.serialize(body)
        } returns ""
        val stream = ByteArrayOutputStream()

        // when
        networkClient.write(body, stream)

        // then
        assertThat(stream.toString()).isEmpty()
    }

    @Test
    fun `write incorrect body`() {
        // given
        val body = mapOf("firstKey" to "firstValue")
        every {
            serializer.serialize(body)
        } throws QonversionException(ErrorCode.Serialization)
        val stream = ByteArrayOutputStream()

        // when and then
        assertThatQonversionExceptionThrown(ErrorCode.BadNetworkRequest) {
            networkClient.write(body, stream)
        }
    }

    @Test
    fun `handle correct response`() {
        // given
        val connection = mockk<HttpURLConnection>()
        val expectedResponseCode = 200
        val expectedResponseBody = "Success"
        val expectedDeserializedResponse = mapOf("someKey" to "someValue")
        val inputStream = ByteArrayInputStream(expectedResponseBody.toByteArray())
        every {
            connection.responseCode
        } returns expectedResponseCode
        every {
            connection.inputStream
        } returns inputStream
        every {
            serializer.deserialize(expectedResponseBody)
        } returns expectedDeserializedResponse

        // when
        val response = networkClient.handleResponse(connection)

        // then
        assertThat(response.code).isEqualTo(expectedResponseCode)
        assertThat(response.payload).isEqualTo(expectedDeserializedResponse)
        verify(exactly = 0) { connection.errorStream }
    }

    @Test
    fun `handle failed response`() {
        // given
        val connection = mockk<HttpURLConnection>()
        val expectedResponseCode = 400
        val expectedResponseBody = "Bad request"
        val expectedDeserializedResponse = mapOf("someKey" to "someValue")
        val inputStream = ByteArrayInputStream(expectedResponseBody.toByteArray())
        every {
            connection.responseCode
        } returns expectedResponseCode
        every {
            connection.errorStream
        } returns inputStream
        every {
            serializer.deserialize(expectedResponseBody)
        } returns expectedDeserializedResponse

        // when
        val response = networkClient.handleResponse(connection)

        // then
        assertThat(response.code).isEqualTo(expectedResponseCode)
        assertThat(response.payload).isEqualTo(expectedDeserializedResponse)
        verify(exactly = 0) { connection.inputStream }
    }

    @Test
    fun `handle internal server error response`() {
        // given
        val connection = mockk<HttpURLConnection>()
        val expectedResponseCode = 500
        val expectedResponseBody = "Internal server error"
        val inputStream = ByteArrayInputStream(expectedResponseBody.toByteArray())
        every {
            connection.responseCode
        } returns expectedResponseCode
        every {
            connection.errorStream
        } returns inputStream

        // when
        val response = networkClient.handleResponse(connection)

        // then
        assertThat(response.code).isEqualTo(expectedResponseCode)
        assertThat(response.payload).isEqualTo(emptyMap<Any, Any>())
        verify(exactly = 0) { connection.inputStream }
        verify(exactly = 0) { serializer.deserialize(any()) }
    }

    @Test
    fun `read correct response`() {
        // given
        val body = "Success"
        val inputStream = ByteArrayInputStream(body.toByteArray())

        // when
        val response = networkClient.read(inputStream)

        // then
        assertThat(response).isEqualTo(body)
    }

    @Test
    fun `read empty response`() {
        // given
        val body = ""
        val inputStream = ByteArrayInputStream(body.toByteArray())

        // when
        val response = networkClient.read(inputStream)

        // then
        assertThat(response).isEqualTo(body)
    }

    @Test
    fun `read multiline response`() {
        // given
        val body = """
            This is simple
            multiline response
            to test line-by-line
            reading.
        """
        val expectedResponse = body.trimIndent().replace("\n", "")
        val inputStream = ByteArrayInputStream(body.toByteArray())

        // when
        val response = networkClient.read(inputStream)

        // then
        assertThat(response).isEqualTo(expectedResponse)
    }

    private fun prepareConnection(): HttpURLConnection {
        return URL("https://qonversion.io").openConnection() as HttpURLConnection
    }
}
