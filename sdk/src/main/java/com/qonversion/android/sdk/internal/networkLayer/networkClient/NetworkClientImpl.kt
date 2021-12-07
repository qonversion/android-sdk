package com.qonversion.android.sdk.internal.networkLayer.networkClient

import androidx.annotation.RawRes
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.RawResponse
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.RequestSerializer
import com.qonversion.android.sdk.internal.networkLayer.utils.isSuccessHttpCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

internal class NetworkClientImpl(
    private val serializer: RequestSerializer
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

    // Internal for tests.
    internal fun parseUrl(url: String): URL {
        return try {
            URL(url)
        } catch (cause: MalformedURLException) {
            throw QonversionException(
                ErrorCode.NetworkRequestExecution,
                "Wrong url - \"$url\"",
                cause
            )
        }
    }

    // Internal for tests.
    internal fun connect(url: URL): HttpURLConnection {
        return try {
            url.openConnection() as HttpURLConnection
        } catch (cause: IOException) {
            throw QonversionException(
                ErrorCode.NetworkRequestExecution,
                "Connection opening failed",
                cause
            )
        }
    }

    // Internal for tests.
    internal fun prepareHeaders(connection: HttpURLConnection, headers: Map<String, String>) {
        headers.forEach {
            connection.addRequestProperty(it.key, it.value)
        }
        connection.addRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("Accept", "application/json")

    }

    // Internal for tests.
    internal fun write(body: Map<String, Any?>, stream: OutputStream) {
        val requestPayload = serializer.serialize(body)
        val outputStreamWriter = OutputStreamWriter(stream, "utf-8")

        try {
            BufferedWriter(outputStreamWriter).use { bw ->
                bw.write(requestPayload)
                bw.flush()
            }
        } catch (cause: IOException) {
            throw QonversionException(
                ErrorCode.NetworkRequestExecution,
                "Failed to send payload",
                cause
            )
        }
    }

    // Internal for tests
    internal fun handleResponse(connection: HttpURLConnection): RawResponse {
        val code = connection.responseCode
        val stream = if (code.isSuccessHttpCode) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val response = read(stream)
        return RawResponse(code, response)
    }

    // Internal for tests
    internal fun read(stream: InputStream): Any {
        val inputStreamReader = InputStreamReader(stream, "utf-8")
        BufferedReader(inputStreamReader).use { br ->
            val responseStringBuilder = StringBuilder()
            var responseLine: String?
            try {
                while (br.readLine().also { responseLine = it } != null) {
                    responseStringBuilder.append(responseLine!!.trim { it <= ' ' })
                }
            } catch (cause: IOException) {
                throw QonversionException(
                    ErrorCode.NetworkRequestExecution,
                    "Failed to read response",
                    cause
                )
            }
            val responsePayload = responseStringBuilder.toString()
            return serializer.deserialize(responsePayload)
        }
    }
}
