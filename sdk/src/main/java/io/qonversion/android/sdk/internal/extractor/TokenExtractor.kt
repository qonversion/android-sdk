package io.qonversion.android.sdk.internal.extractor

import io.qonversion.android.sdk.internal.dto.BaseResponse
import retrofit2.Response

internal class TokenExtractor :
    Extractor<Response<BaseResponse<io.qonversion.android.sdk.internal.dto.Response>>> {
    override fun extract(response: Response<BaseResponse<io.qonversion.android.sdk.internal.dto.Response>>?): String {
        return response?.body()?.let {
            it.data.clientUid ?: ""
        } ?: ""
    }
}
