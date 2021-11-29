package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.JsonSerializer
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.RequestSerializer
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class NetworkClientImpl(
    private val serializer: RequestSerializer = JsonSerializer()
): NetworkClient {

    override suspend fun execute(request: Request): Response {
        return withContext(Dispatchers.IO) {
            val url = try {
                URL(request.url)
            } catch (cause: MalformedURLException) {
                throw QonversionException(
                    ErrorCode.NETWORK_REQUEST_EXECUTION,
                    "Wrong url - \"${request.url}\"",
                    cause
                )
            }

            val connection = try {
                url.openConnection() as HttpURLConnection
            } catch (cause: IOException) {
                throw QonversionException(
                    ErrorCode.NETWORK_REQUEST_EXECUTION,
                    "Connection opening failed",
                    cause
                )
            }

            connection.requestMethod = request.type.toString()
            try {
                request.headers.forEach {
                    connection.addRequestProperty(it.key, it.value)
                }
            } catch (cause: NullPointerException) {
                throw QonversionException(
                    ErrorCode.NETWORK_REQUEST_EXECUTION,
                    "Header key can not be null",
                    cause
                )
            }
            connection.addRequestProperty("Content-Type", "application/json")
            connection.addRequestProperty("Accept", "application/json")

            request.body?.let {
                connection.doOutput = true
                write(it, connection)
            }
            val code = connection.responseCode
            val response = read(connection)
            Response(code, response)
        }
    }

    private fun write(body: Map<String, Any?>, connection: HttpURLConnection) {
        val requestPayload = serializer.serialize(body)
        val outputStreamWriter = OutputStreamWriter(connection.outputStream, "utf-8")

        try {
            BufferedWriter(outputStreamWriter).use { bw ->
                bw.write(requestPayload)
                bw.flush()
            }
        } catch (cause: IOException) {
            throw QonversionException(
                ErrorCode.NETWORK_REQUEST_EXECUTION,
                "Failed to send payload",
                cause
            )
        }
    }

    private fun read(connection: HttpURLConnection): Any {
        val inputStreamReader = InputStreamReader(connection.inputStream, "utf-8")
        BufferedReader(inputStreamReader).use { br ->
            val responseStringBuilder = StringBuilder()
            var responseLine: String?
            try {
                while (br.readLine().also { responseLine = it } != null) {
                    responseStringBuilder.append(responseLine!!.trim { it <= ' ' })
                }
            } catch (cause: IOException) {
                throw QonversionException(
                    ErrorCode.NETWORK_REQUEST_EXECUTION,
                    "Failed to read response",
                    cause
                )
            }
            val responsePayload = responseStringBuilder.toString()
            return serializer.deserialize(responsePayload)
        }
    }
}