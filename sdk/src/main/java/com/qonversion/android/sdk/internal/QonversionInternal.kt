package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.Environment

internal class QonversionInternal(config: QonversionConfig) : Qonversion {

    init {
        InternalConfig.projectKey = config.projectKey
        InternalConfig.launchMode = config.launchMode
        InternalConfig.environment = config.environment
    }

    override fun setEnvironment(environment: Environment) {
        InternalConfig.environment = environment
    }

    override fun finish() {
        TODO("Not yet implemented")
    }
}
