package com.qonversion.android.sdk.internal.networkLayer.networkClient

import com.qonversion.android.sdk.internal.networkLayer.dto.Request
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.JsonSerializer
import com.qonversion.android.sdk.internal.networkLayer.requestSerializer.RequestSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    fun test() {
        val r = Request.post(
            "https://postman-echo.com/post",
            mapOf(Pair("a", "av"), Pair("b", "bv")),
            mapOf(Pair("someval", 123))
        )

        CoroutineScope(Dispatchers.IO).launch {
            execute(r)
        }
    }

    override suspend fun execute(request: Request): Response {
        return if (request.type == Request.Type.POST) {
            post(request)
        } else {
            get(request)
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

    private fun post(request: Request): Response {
        val url = URL(request.url)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        request.headers.forEach {
            connection.addRequestProperty(it.key, it.value)
        }
        connection.addRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("Accept", "application/json")
        connection.doOutput = true

        if (request.body == null) {
            throw IllegalStateException("Request body can't be null for POST request")
        }

        write(request.body, connection)
        val code = connection.responseCode
        val response = read(connection)

        return Response(code, response)
    }

    private fun get(request: Request): Response {
        return Response(0, Unit)
    }
}