package com.qonversion.android.sdk.internal.logger

import android.util.Log
import com.qonversion.android.sdk.BuildConfig
import com.qonversion.android.sdk.internal.Constants.CRASH_LOG_FILE_SUFFIX
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintStream
import java.util.UUID

private const val TAG = "QExceptionHandler"

internal class ExceptionHandler(
    private val appPackageName: String,
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler?,
    private val reportsDir: File
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        if (isQonversionException(exception)) {
            storeException(exception)
        }
        defaultExceptionHandler?.uncaughtException(thread, exception)
    }

    private fun storeException(exception: Throwable) {
        val exceptionJson = exception.toJSON()
        val uuid = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val file = File(reportsDir, "$uuid-$timestamp$CRASH_LOG_FILE_SUFFIX")
        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(FileWriter(file))
            writer.write(exceptionJson.toString())
            writer.flush()
        } catch (cause: Exception) {
            Log.e(TAG, "Failed to save exception info to a file", cause)
        } finally {
            try {
                writer?.close()
            } catch (cause: IOException) {
                Log.e(TAG, "Failed to finish saving exception info to a file", cause)
            }
        }
    }

    private fun Throwable.toJSON(): JSONObject {
        val data = JSONObject()
        val traces = JSONArray()
        var throwable: Throwable? = this
        do {
            throwable?.let {
                traces.put(it.traceJSON())
            }
            throwable = throwable?.cause
        } while (throwable != null)

        data.put("traces", traces)
        data.put("title", this)
        data.put("place", stackTrace.firstOrNull() ?: "")
        return data
    }

    private fun Throwable.traceJSON(): JSONObject {
        val data = JSONObject()
        val elements = JSONArray()

        stackTrace.iterator().forEach { stackTraceElement ->
            val elementData = JSONObject()
            elementData.put("class", stackTraceElement.className)
            elementData.put("file", stackTraceElement.fileName)
            elementData.put("method", stackTraceElement.methodName)
            elementData.put("line", stackTraceElement.lineNumber)
            elements.put(elementData)
        }

        data.put("rawStackTrace", rawStackTrace())
        data.put("class", javaClass.name)
        data.put("message", message)
        data.put("elements", elements)

        return data
    }

    private fun Throwable.rawStackTrace(): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)

            printStackTrace(printStream)

            printStream.close()
            outputStream.close()

            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    private fun isQonversionException(exception: Throwable): Boolean {
        val sdkPackageName = BuildConfig.LIBRARY_PACKAGE_NAME
        for (element in exception.stackTrace) {
            if (element.className.contains(sdkPackageName)) {
                return true
            }

            // Skip exceptions caused by clients' code
            if (element.className.contains(appPackageName)) {
                return false
            }
        }

        return false
    }
}
