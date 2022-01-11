package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.QonversionInternal
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

class Qonversion private constructor(private val qonversionInternal: QonversionInternal) {

    companion object {

        private var backingInstance: Qonversion? = null

        val sharedInstance get() = backingInstance ?: throw QonversionException(ErrorCode.NotInitialized)

        fun configure(config: QonversionConfig): Qonversion {
            TODO()
        }
    }
}
