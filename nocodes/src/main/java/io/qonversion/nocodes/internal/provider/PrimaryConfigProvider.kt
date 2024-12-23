package io.qonversion.nocodes.internal.provider

import io.qonversion.nocodes.internal.dto.config.PrimaryConfig

internal interface PrimaryConfigProvider {

    val primaryConfig: PrimaryConfig
}
