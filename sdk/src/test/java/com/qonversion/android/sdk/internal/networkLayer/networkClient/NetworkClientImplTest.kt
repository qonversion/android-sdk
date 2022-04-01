package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.assertThatQonversionExceptionThrown
import com.qonversion.android.sdk.coAssertThatQonversionExceptionThrown
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.common.serializers.Serializer
import com.qonversion.android.sdk.internal.networkLayer.dto.RawResponse
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

@ExperimentalCoroutinesApi
internal class NetworkClientImplTest {

    private lateinit var networkClient: NetworkClientImpl

    private var serializer: Serializer = mockk()

    @BeforeEach
    fun setUp() {
        networkClient = NetworkClientImpl(serializer)
    }

    @Nested
    inner class ExecuteTest {

        private val testUrlStr = "test url"
        private val mockHeaders = mapOf("one" to "three")
        private val mockBody = mapOf("to be or not" to "be")
        private val mockUrl = mockk<URL>()
        private val mockConnection = mockk<HttpURLConnection>(relaxed = true)
        private val mockOutputStream = mockk<OutputStream>()
        private val mockResponse = mockk<RawResponse>()

        @BeforeEach
        fun setUp() {
            networkClient = spyk(networkClient)

            every { mockConnection.outputStream } returns mockOutputStream
            every { networkClient.connect(mockUrl) } returns mockConnection
            every { networkClient.parseUrl(testUrlStr) } returns mockUrl
            every { networkClient.prepareHeaders(mockConnection, any()) } just runs
            every { networkClient.write(any(), mockOutputStream) } just runs
            every { networkClient.handleResponse(mockConnection) } returns mockResponse
        }

        @Test
        fun `execute post request`() = runTest {
            // given
            val request = Request.post(testUrlStr, mockHeaders, mockBody)

            // when
            val res = networkClient.execute(request)

            // then
            assertThat(res).isSameAs(mockResponse)
            verifyOrder {
                networkClient.parseUrl(testUrlStr)
                networkClient.connect(mockUrl)
                mockConnection.requestMethod = request.type.toString()
                networkClient.prepareHeaders(mockConnection, mockHeaders)
                mockConnection.doOutput = true
                networkClient.write(mockBody, mockOutputStream)
                networkClient.handleResponse(mockConnection)
            }
        }

        @Test
        fun `execute get request`() = runTest {
            // given
            val request = Request.get(testUrlStr, mockHeaders)

            // when
            val res = networkClient.execute(request)

            // then
            assertThat(res).isSameAs(mockResponse)
            verifyOrder {
                networkClient.parseUrl(testUrlStr)
                networkClient.connect(mockUrl)
                mockConnection.requestMethod = request.type.toString()
                networkClient.prepareHeaders(mockConnection, mockHeaders)
                networkClient.handleResponse(mockConnection)
            }
            verify(exactly = 0) {
                mockConnection.doOutput = any()
                networkClient.write(any(), any())
            }
        }
    }

    @Nested
    inner class ParseUrlTest {
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
    }

    @Nested
    inner class ConnectTest {
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
            every { url.openConnection() } throws IOException()

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.NetworkRequestExecution) {
                networkClient.connect(url)
            }
        }

        @Test
        fun `connect with incorrect return type`() {
            // given
            val url = mockk<URL>()
            val unexpectedConnection = mockk<URLConnection>()
            every { url.openConnection() } returns unexpectedConnection

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.NetworkRequestExecution) {
                networkClient.connect(url)
            }
        }

        @Test
        fun `connect with null return`() {
            // given
            val url = mockk<URL>()
            every { url.openConnection() } returns null

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.NetworkRequestExecution) {
                networkClient.connect(url)
            }
        }

        @Test
        fun `connect with unexpected exception`() {
            // given
            val url = mockk<URL>()
            val expException = IllegalStateException()
            every { url.openConnection() } throws expException

            // when
            val exception = try {
                networkClient.connect(url)
                null
            } catch (e: Exception) {
                e
            }

            // then
            assertThat(exception).isSameAs(expException)
        }
    }

    @Nested
    inner class PrepareHeadersTest {
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
    }

    @Nested
    inner class WriteTest {
        @Test
        fun `write correct body`() {
            // given
            val body = mapOf("firstKey" to "firstValue", "secondKey" to "secondValue")
            val expectedBody = "someTestBody"
            every { serializer.serialize(body) } returns expectedBody
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
            every { serializer.serialize(body) } returns ""
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
            every { serializer.serialize(body) } throws QonversionException(ErrorCode.Serialization)
            val stream = ByteArrayOutputStream()

            // when and then
            assertThatQonversionExceptionThrown(ErrorCode.BadNetworkRequest) {
                networkClient.write(body, stream)
            }
        }

        @Test
        fun `writing to stream fails`() {
            // given
            val body = mapOf("firstKey" to "firstValue")
            val expectedBody = "someTestBody"
            every { serializer.serialize(body) } returns expectedBody
            val stream = ByteArrayOutputStream()

            val exception = IOException("test error")
            mockkConstructor(BufferedWriter::class)
            every { anyConstructed<BufferedWriter>().write(expectedBody) } throws exception

            // when and then
            val resException = assertThatQonversionExceptionThrown(ErrorCode.NetworkRequestExecution) {
                networkClient.write(body, stream)
            }
            assertThat(resException.cause).isSameAs(exception)

            unmockkConstructor(BufferedWriter::class)
        }
    }

    @Nested
    inner class HandleResponse {
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
    }

    @Nested
    inner class ReadTest {
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

        @Test
        // Due to unexplainable mockk issues this tests only works when the whole file tests are executed.
        // Otherwise it gets stuck on `every` clause execution.
        fun `reading from stream fails`() {
            // given
            val body = "Success"
            val inputStream = ByteArrayInputStream(body.toByteArray())

            val exception = IOException("test error")
            mockkConstructor(BufferedReader::class)
            every { anyConstructed<BufferedReader>().readLine() } throws exception

            // when and then
            val resException = assertThatQonversionExceptionThrown(ErrorCode.NetworkRequestExecution) {
                networkClient.read(inputStream)
            }
            assertThat(resException.cause).isSameAs(exception)

            unmockkConstructor(BufferedReader::class)
        }
    }

    private fun prepareConnection(): HttpURLConnection {
        return URL("https://qonversion.io").openConnection() as HttpURLConnection
    }
}
