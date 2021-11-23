package com.qonversion.android.sdk.old.extractor

import com.qonversion.android.sdk.old.dto.BaseResponse
import retrofit2.Response

class TokenExtractor :
    Extractor<Response<BaseResponse<com.qonversion.android.sdk.old.dto.Response>>> {
    override fun extract(response: Response<BaseResponse<com.qonversion.android.sdk.old.dto.Response>>?): String {
        return response?.body()?.let {
            it.data.clientUid ?: ""
        } ?: ""
    }
}
