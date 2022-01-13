package com.qonversion.android.sdk

import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

class QonversionConfig private constructor(val projectKey: String) {

    data class Builder(
        private val projectKey: String,
    ) {
        fun build(): QonversionConfig {
            if (projectKey.isBlank()) {
                throw QonversionException(ErrorCode.ConfigPreparation, "Project key is empty")
            }
            return QonversionConfig(projectKey)
        }
    }
}
