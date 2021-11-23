package com.qonversion.android.sdk.old.storage

import com.qonversion.android.sdk.old.dto.BaseResponse
import com.qonversion.android.sdk.old.extractor.Extractor
import com.qonversion.android.sdk.old.extractor.TokenExtractor
import com.qonversion.android.sdk.old.storage.Util.Companion.CLIENT_ID
import com.qonversion.android.sdk.old.storage.Util.Companion.CLIENT_TARGET_ID
import com.qonversion.android.sdk.old.storage.Util.Companion.CLIENT_UID
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class TokenExtractorTest {

    private lateinit var tokenExtractor: Extractor<Response<BaseResponse<com.qonversion.android.sdk.old.dto.Response>>>

    @Before
    fun setup() {
        tokenExtractor = TokenExtractor()
    }

    @Test
    fun extractFromEmptyResponse() {
        val response = Response.success(BaseResponse(
            true,
            com.qonversion.android.sdk.old.dto.Response(
                "",
                "",
                ""
            )
        ))
        val token = tokenExtractor.extract(response)
        Assert.assertEquals(token, "")
    }

    @Test
    fun extractFromNullResponseBody() {
        val token = tokenExtractor.extract(Response.success(null))
        Assert.assertEquals(token, "")
    }

    @Test
    fun extractFromNullResponse() {
        val token = tokenExtractor.extract(null)
        Assert.assertEquals(token, "")
    }

    @Test
    fun extractFromCorrectResponse() {
        val response = Response.success(BaseResponse(
            true,
            com.qonversion.android.sdk.old.dto.Response(
                CLIENT_ID,
                CLIENT_UID,
                CLIENT_TARGET_ID
            )
        ))
        val token = tokenExtractor.extract(response)
        Assert.assertEquals(token, CLIENT_UID)
    }
}