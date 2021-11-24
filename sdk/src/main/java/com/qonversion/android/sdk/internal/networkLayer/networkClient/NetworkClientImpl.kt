package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.JsonSerializer
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.RequestSerializer
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL

class NetworkClientImpl(
    private val serializer: RequestSerializer = JsonSerializer()
): NetworkClient {

    override suspend fun execute(request: Request): Response {
        return withContext(Dispatchers.IO) {
            val url = URL(request.url)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = request.type.toString()
            request.headers.forEach {
                connection.addRequestProperty(it.key, it.value)
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
        BufferedWriter(outputStreamWriter).use { bw ->
            bw.write(requestPayload)
            bw.flush()
        }
    }

    private fun read(connection: HttpURLConnection): Any {
        val inputStreamReader = InputStreamReader(connection.inputStream, "utf-8")
        BufferedReader(inputStreamReader).use { br ->
            val responseStringBuilder = StringBuilder()
            var responseLine: String?
            while (br.readLine().also { responseLine = it } != null) {
                responseStringBuilder.append(responseLine!!.trim { it <= ' ' })
            }
            val responsePayload = responseStringBuilder.toString()
            return serializer.deserialize(responsePayload)
        }
    }
}