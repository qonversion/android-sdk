package io.qonversion.android.sdk.internal.provider

import io.qonversion.android.sdk.dto.QEnvironment

internal interface EnvironmentProvider {

    val apiUrl: String

    val environment: QEnvironment

    val isSandbox: Boolean
}
