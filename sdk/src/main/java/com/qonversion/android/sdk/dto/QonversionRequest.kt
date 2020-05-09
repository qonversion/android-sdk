package com.qonversion.android.sdk.dto

interface QonversionRequest {
    fun authorize(clientUid: String)
    fun isAuthorized(): Boolean
}