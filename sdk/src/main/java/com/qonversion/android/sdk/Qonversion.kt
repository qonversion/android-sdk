package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

class Qonversion private constructor(private val qonversionInternal: QonversionInternal) {

    companion object {

        private var backingInstance: Qonversion? = null

        val sharedInstance: Qonversion
            get() = backingInstance ?: throw QonversionException(ErrorCode.NotInitialized)

        fun initialize(config: QonversionConfig): Qonversion {
            val qonversionInternal = QonversionInternal(config)
            val instance = Qonversion(qonversionInternal)
            backingInstance = instance

            return instance
        }
    }

    fun finish() {}
}
