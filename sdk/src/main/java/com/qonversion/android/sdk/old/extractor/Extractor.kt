package com.qonversion.android.sdk.old.extractor

interface Extractor<T> {
    fun extract(response: T?): String
}
