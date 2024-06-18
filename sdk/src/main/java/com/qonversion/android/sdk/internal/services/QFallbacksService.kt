package com.qonversion.android.sdk.internal.services

import android.app.Application
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import com.qonversion.android.sdk.R
import com.qonversion.android.sdk.dto.QFallbackObject
import com.qonversion.android.sdk.internal.application
import com.qonversion.android.sdk.internal.dto.config.CacheConfig
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.CacheConfigProvider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Paths
import javax.inject.Inject

internal class QFallbacksService(
    private val context: Application,
    private val cacheConfigProvider: CacheConfigProvider,
    moshi: Moshi,
    private val logger: Logger
) {
    private val jsonAdapter: JsonAdapter<QFallbackObject> = moshi.adapter(QFallbackObject::class.java)

    fun obtainFallbackData(): QFallbackObject? {
        return try {
            val json: String = getStringFromFile("qonversion_fallbacks.json")
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
        val fallbackFileIdentifier = cacheConfigProvider.cacheConfig.fallbackFileIdentifier
        val fileInputStream = if (fallbackFileIdentifier != null) {
            context.resources.openRawResource(fallbackFileIdentifier)
        } else {
            context.assets.open(filePath)
        }

        val result = convertStreamToString(fileInputStream)
        fileInputStream.close()

        return result
    }
}