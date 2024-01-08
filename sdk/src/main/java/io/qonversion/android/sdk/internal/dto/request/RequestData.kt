package io.qonversion.android.sdk.internal.dto.request

import io.qonversion.android.sdk.internal.dto.Environment

internal abstract class RequestData {
    abstract val installDate: Long
    abstract val device: Environment
    abstract val version: String
    abstract val accessToken: String
    abstract val clientUid: String?
    abstract val receipt: String
    abstract val debugMode: String
}
