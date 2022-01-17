package com.qonversion.android.sdk.internal

import com.qonversion.android.sdk.dto.Environment

internal interface EnvironmentProvider {

    val environment: Environment

    val isSandbox: Boolean
}
