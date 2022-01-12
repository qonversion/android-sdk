package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.QonversionConfig

internal class QonversionInternal(config: QonversionConfig) {

    init {
        InternalConfig.projectKey = config.projectKey
        InternalConfig.environment = config.environment
    }
}
