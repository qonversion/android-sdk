package com.qonversion.android.sdk.dto.request

interface QonversionRequest {
    fun authorize(clientUid: String)
    fun isAuthorized(): Boolean
}