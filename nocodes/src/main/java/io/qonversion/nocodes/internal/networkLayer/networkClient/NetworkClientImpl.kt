package io.qonversion.nocodes.internal.networkLayer.networkClient

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.serializers.Serializer
import io.qonversion.nocodes.internal.common.TimeoutConstants
import io.qonversion.nocodes.internal.networkLayer.dto.RawResponse
import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.networkLayer.utils.isInternalServerErrorHttpCode
import io.qonversion.nocodes.internal.networkLayer.utils.isSuccessHttpCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

private const val NETWORK_ENCODING = "utf-8"

internal class NetworkClientImpl(
    private val serializer: Serializer,
    private val isFallbackAvailable: Boolean = false
) : NetworkClient {
    override suspend fun execute(request: Request): RawResponse {
        return withContext(Dispatchers.IO) {
            val url = parseUrl(request.url)
            val connection = connect(url)

            connection.requestMethod = request.type.toString()
            prepareHeaders(connection, request.headers)

            request.body?.let {
                connection.doOutput = true
                write(it, connection.outputStream)
            }

            handleResponse(connection)
        }
    }

    internal fun parseUrl(url: String): URL {
        return try {
            URL(url)
        } catch (cause: MalformedURLException) {
            throw NoCodesException(
                ErrorCode.BadNetworkRequest,
                "Wrong url - \"$url\"",
                cause
            )
        }
    }

    internal fun connect(url: URL): HttpURLConnection {
        return try {
            val connection = url.openConnection() as HttpURLConnection
            
            // Set smart timeout based on fallback availability
            val timeout = if (isFallbackAvailable) {
                TimeoutConstants.FALLBACK_AVAILABLE_TIMEOUT
            } else {
                TimeoutConstants.DEFAULT_TIMEOUT
            }
            
            connection.connectTimeout = timeout.toInt()
            connection.readTimeout = timeout.toInt()
            
            connection
        } catch (cause: Exception) {
            throw if (cause is IOException || cause is ClassCastException || cause is NullPointerException) {
                NoCodesException(
                    ErrorCode.NetworkRequestExecution,
                    "Connection opening failed",
                    cause
                )
            } else cause
        }
    }

    internal fun prepareHeaders(connection: HttpURLConnection, headers: Map<String, String>) {
        headers.forEach {
            connection.addRequestProperty(it.key, it.value)
        }
        connection.addRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("Accept", "application/json")
    }

    internal fun write(body: Map<String, Any?>, stream: OutputStream) {
        val requestPayload = try {
            serializer.serialize(body)
        } catch (cause: NoCodesException) {
            throw NoCodesException(ErrorCode.BadNetworkRequest, cause = cause)
        }

        val outputStreamWriter = OutputStreamWriter(stream, NETWORK_ENCODING)

        try {
            BufferedWriter(outputStreamWriter).use { bw ->
                bw.write(requestPayload)
                bw.flush()
            }
        } catch (cause: IOException) {
            throw NoCodesException(
                ErrorCode.NetworkRequestExecution,
                "Failed to send payload",
                cause
            )
        }
    }

    internal fun handleResponse(connection: HttpURLConnection): RawResponse {
        val code = connection.responseCode
        val stream = if (code.isSuccessHttpCode) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = if (code.isInternalServerErrorHttpCode) {
            emptyMap<Any, Any>()
        } else {
            val responsePayload = read(stream)
            serializer.deserialize(responsePayload)
        }
        return RawResponse(code, response)
    }

    internal fun read(stream: InputStream): String {
        val inputStreamReader = InputStreamReader(stream, NETWORK_ENCODING)
        BufferedReader(inputStreamReader).use { br ->
            val responseStringBuilder = StringBuilder()
            var responseLine: String?
            try {
                while (br.readLine().also { responseLine = it } != null) {
                    responseStringBuilder.append(responseLine!!.trim { it <= ' ' })
                }
            } catch (cause: IOException) {
                throw NoCodesException(
                    ErrorCode.NetworkRequestExecution,
                    "Failed to read response",
                    cause
                )
            }
            return responseStringBuilder.toString()
        }
    }
}
