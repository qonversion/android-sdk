package com.qonversion.android.sdk.extractor

interface Extractor<T> {
    fun extract(response: T?): String
}