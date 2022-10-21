package com.qonversion.android.sdk.internal.extractor

import com.qonversion.android.sdk.internal.dto.BaseResponse
import retrofit2.Response

internal class TokenExtractor :
    Extractor<Response<BaseResponse<com.qonversion.android.sdk.internal.dto.Response>>> {
    override fun extract(response: Response<BaseResponse<com.qonversion.android.sdk.internal.dto.Response>>?): String {
        return response?.body()?.let {
            it.data.clientUid ?: ""
        } ?: ""
    }
}
