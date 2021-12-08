package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.RequestSerializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

internal class NetworkClientImplTest {

    private lateinit var networkClient: NetworkClientImpl

    private var serializer: RequestSerializer = mockk()

    @Before
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
        assertThatThrownBy {
            runBlocking {
                networkClient.parseUrl(url)
            }
        }.isInstanceOf(QonversionException::class.java)
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
        assertThatThrownBy {
            networkClient.connect(url)
        }.isInstanceOf(QonversionException::class.java)
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
    fun `read correct response`() {
        // given
        val body = "Success"
        val expectedDeserializedResponse = mapOf("someKey" to "someValue")
        val inputStream = ByteArrayInputStream(body.toByteArray())
        every {
            serializer.deserialize(body)
        } returns expectedDeserializedResponse

        // when
        val response = networkClient.read(inputStream)

        // then
        assertThat(response).isEqualTo(expectedDeserializedResponse)
    }

    @Test
    fun `read empty response`() {
        // given
        val body = ""
        val expectedDeserializedResponse = emptyMap<String, Any?>()
        val inputStream = ByteArrayInputStream(body.toByteArray())
        every {
            serializer.deserialize(body)
        } returns expectedDeserializedResponse

        // when
        val response = networkClient.read(inputStream)

        // then
        assertThat(response).isEqualTo(expectedDeserializedResponse)
    }

    private fun prepareConnection(): HttpURLConnection {
        return URL("https://qonversion.io").openConnection() as HttpURLConnection
    }
}