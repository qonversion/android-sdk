package com.qonversion.android.sdk.internal.services

import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.internal.logger.Logger
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject

internal class QFallbacksService @Inject constructor(
    moshi: Moshi,
    private val logger: Logger
) {
    private val jsonAdapter: JsonAdapter<QFallbackObject> = moshi.adapter(QFallbackObject::class.java)
    fun obtainFallbackData(): QFallbackObject? {
        return try {
            val json: String = getStringFromFile("qonversion_fallbscks.json")
            val fallbackData: QFallbackObject? = jsonAdapter.fromJson(json)

            fallbackData
        } catch (e: Exception) {
            logger.warn("Failed to parse Qonversion fallback file: " + e.message)
            null
        }
    }

    @Throws(java.lang.Exception::class)
    fun convertStreamToString(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append("\n")
        }
        reader.close()

        return stringBuilder.toString()
    }

    @Throws(java.lang.Exception::class)
    fun getStringFromFile(filePath: String): String {
        val file = File(filePath)
        val fileInputStream = FileInputStream(file)
        val result = convertStreamToString(fileInputStream)
        fileInputStream.close()

        return result
    }

}