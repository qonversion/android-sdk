package com.qonversion.android.sdk.extractor

import com.qonversion.android.sdk.dto.BaseResponse
import retrofit2.Response

class TokenExtractor :
    Extractor<Response<BaseResponse<com.qonversion.android.sdk.dto.Response>>> {
    override fun extract(response: Response<BaseResponse<com.qonversion.android.sdk.dto.Response>>?): String {
        return response?.body()?.let {
            it.data.clientUid ?: ""
        } ?: ""
    }
}
