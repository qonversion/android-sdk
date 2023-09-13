package com.qonversion.android.sdk.internal.logger

import android.content.Context
import android.util.Log
import com.qonversion.android.sdk.internal.Constants.CRASH_LOG_FILE_SUFFIX
import com.qonversion.android.sdk.internal.InternalConfig
import com.qonversion.android.sdk.internal.repository.QRepository
import com.qonversion.android.sdk.internal.api.ApiHeadersProvider
import com.qonversion.android.sdk.internal.dto.request.CrashRequest
import com.qonversion.android.sdk.internal.isDebuggable
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import java.io.BufferedReader
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "QExceptionManager"

internal class QExceptionManager @Inject constructor(
    private val repository: QRepository,
    private val intervalConfig: InternalConfig,
    private val headersProvider: ApiHeadersProvider,
    moshi: Moshi
) : ExceptionManager {

    private val exceptionAdapter: JsonAdapter<CrashRequest.ExceptionInfo> =
        moshi.adapter(CrashRequest.ExceptionInfo::class.java)

    private lateinit var reportsDir: File
    private lateinit var contextRef: WeakReference<Context>

    override fun initialize(context: Context) {
        reportsDir = context.filesDir
        contextRef = WeakReference(context)

        if (context.isDebuggable) {
            return
        }

        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val newExceptionHandler = ExceptionHandler(
            context.packageName,
            defaultExceptionHandler,
            reportsDir
        )
        Thread.setDefaultUncaughtExceptionHandler(newExceptionHandler)

        sendCrashReportsInBackground()
    }

    private fun sendCrashReportsInBackground() {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        executor.execute {
            val fileNames = getAvailableReportNames()
            fileNames.forEach { filename ->
                getContentOfCrashReport(filename)?.let {
                    val data = prepareCrashData(it)
                    repository.crashReport(
                        data,
                        { contextRef.get()?.deleteFile(filename) },
                        { error -> Log.e(TAG, "Failed to send crash report to API - $error") }
                    )
                }
            }
        }
    }

    private fun getAvailableReportNames(): List<String> {
        if (!reportsDir.exists() && !reportsDir.mkdir()) {
            return emptyList()
        }

        val filter = FilenameFilter { _, name -> name.endsWith(CRASH_LOG_FILE_SUFFIX) }
        return reportsDir.list(filter)?.toList() ?: emptyList()
    }

    private fun getContentOfCrashReport(filename: String): CrashRequest.ExceptionInfo? {
        val context = contextRef.get() ?: run { return null }

        context.getFileStreamPath(filename)?.takeIf { it.exists() } ?: run { return null }
        val content = StringBuilder()
        try {
            BufferedReader(InputStreamReader(context.openFileInput(filename))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    content.appendLine(line)
                }
            }
        } catch (cause: IOException) {
            Log.e(TAG, "Failed to read crash report from the file", cause)
        }
        return try {
            exceptionAdapter.fromJson(content.toString())
        } catch (cause: Exception) {
            Log.e(TAG, "Failed to parse JSON from the crash report file", cause)
            null
        }
    }

    private fun prepareCrashData(exception: CrashRequest.ExceptionInfo): CrashRequest {
        return CrashRequest(exception, CrashRequest.DeviceInfo(
            headersProvider.getPlatform(),
            headersProvider.getPlatformVersion(),
            headersProvider.getSource(),
            headersProvider.getSourceVersion(),
            headersProvider.getProjectKey(),
            intervalConfig.uid
        ))
    }
}
