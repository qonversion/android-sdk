package com.qonversion.android.sdk.old.dto.request

import com.qonversion.android.sdk.old.dto.Environment

abstract class RequestData {
    abstract val installDate: Long
    abstract val device: Environment
    abstract val version: String
    abstract val accessToken: String
    abstract val clientUid: String?
    abstract val receipt: String
    abstract val debugMode: String
}
