package com.qonversion.android.sdk.dto

abstract class RequestData {
    abstract val installDate: Long?
    abstract val device: Environment
    abstract val version: String
    abstract val accessToken: String
    abstract val clientUid: String?
    abstract val customUid: String?
    abstract val receipt: String
}