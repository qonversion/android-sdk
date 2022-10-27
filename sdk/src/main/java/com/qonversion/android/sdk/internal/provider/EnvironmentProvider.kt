package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.dto.Environment

internal interface EnvironmentProvider {

    val environment: Environment

    val isSandbox: Boolean
}
