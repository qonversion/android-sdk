package com.qonversion.android.sdk.internal.extractor

internal interface Extractor<T> {
    fun extract(response: T?): String
}
