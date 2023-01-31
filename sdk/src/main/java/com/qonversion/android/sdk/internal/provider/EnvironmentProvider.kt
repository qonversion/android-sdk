package com.qonversion.android.sdk.internal.provider

import com.qonversion.android.sdk.dto.QEnvironment

internal interface EnvironmentProvider {

    val apiUrl: String

    val environment: QEnvironment

    val isSandbox: Boolean
}
