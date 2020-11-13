package com.qonversion.android.sdk.extractor

import com.qonversion.android.sdk.dto.BaseResponse
import com.qonversion.android.sdk.dto.Response

interface Extractor<T> {
    fun extract(response: T?): String
}