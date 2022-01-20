package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.CacheLifetime
import com.qonversion.android.sdk.dto.Environment
import com.qonversion.android.sdk.dto.LogLevel
import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

interface Qonversion {

    companion object {

        private var backingInstance: Qonversion? = null

        @JvmStatic
        val sharedInstance: Qonversion
            get() = backingInstance ?: throw QonversionException(ErrorCode.NotInitialized)

        @JvmStatic
        fun initialize(config: QonversionConfig): Qonversion {
            return QonversionInternal(config).also {
                backingInstance = it
            }
        }
    }

    fun setEnvironment(environment: Environment)

    fun setLogLevel(logLevel: LogLevel)

    fun setLogTag(logTag: String)

    fun setCacheLifetime(cacheLifetime: CacheLifetime)

    fun finish()
}
