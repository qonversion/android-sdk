package com.qonversion.android.sdk.internal.storage

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.qonversion.android.sdk.internal.dto.BaseResponse
import com.qonversion.android.sdk.internal.dto.Response
import com.qonversion.android.sdk.internal.extractor.Extractor
import com.qonversion.android.sdk.internal.extractor.TokenExtractor
import com.qonversion.android.sdk.internal.storage.Util.Companion.CLIENT_ID
import com.qonversion.android.sdk.internal.storage.Util.Companion.CLIENT_TARGET_ID
import com.qonversion.android.sdk.internal.storage.Util.Companion.CLIENT_UID
import com.qonversion.android.sdk.internal.validator.TokenValidator
import com.qonversion.android.sdk.internal.validator.Validator
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TokenStorageTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var tokenExtractor: Extractor<retrofit2.Response<BaseResponse<Response>>>

    @Before
    fun setup() {
        tokenStorage = TokenStorage(SPMockBuilder().createSharedPreferences(),
            TokenValidator() as Validator<String>
        )
        tokenExtractor = TokenExtractor()
    }

    @Test
    fun saveNotEmptyTokenWhenExistTokenEmpty() {
        val emptyResponse = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                "",
                "",
                ""
            )
        ))
        val emptyToken = tokenExtractor.extract(emptyResponse)
        tokenStorage.save(emptyToken)
        Assert.assertEquals("", tokenStorage.load())
        Assert.assertFalse(tokenStorage.exist())

        val response = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                CLIENT_ID,
                CLIENT_UID,
                CLIENT_TARGET_ID
            )
        ))
        val notEmptyToken = tokenExtractor.extract(response)

        tokenStorage.save(notEmptyToken)
        Assert.assertEquals(CLIENT_UID, tokenStorage.load())
        Assert.assertTrue(tokenStorage.exist())
    }


    @Test
    fun saveEmptyTokenWhenExistTokenEmpty() {
        val firstEmptyResponse = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                "",
                "",
                ""
            )
        ))
        val firstEmptyToken = tokenExtractor.extract(firstEmptyResponse)

        tokenStorage.save(firstEmptyToken)
        Assert.assertEquals("", tokenStorage.load())
        Assert.assertFalse(tokenStorage.exist())

        val secondEmptyResponse = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                "",
                "",
                ""
            )
        ))
        val secondEmptyToken = tokenExtractor.extract(secondEmptyResponse)
        tokenStorage.save(secondEmptyToken)
        Assert.assertEquals("", tokenStorage.load())
        Assert.assertFalse(tokenStorage.exist())
    }

    @Test
    fun saveEmptyTokenWhenNotEmptyTokenExist() {
        val response = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                CLIENT_ID,
                CLIENT_UID,
                CLIENT_TARGET_ID
            )
        ))
        val notEmptyToken = tokenExtractor.extract(response)

        tokenStorage.save(notEmptyToken)
        Assert.assertEquals(CLIENT_UID, tokenStorage.load())
        Assert.assertTrue(tokenStorage.exist())

        val emptyResponse = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                "",
                "",
                ""
            )
        ))
        val emptyToken = tokenExtractor.extract(emptyResponse)
        tokenStorage.save(emptyToken)
        Assert.assertEquals(CLIENT_UID, tokenStorage.load())
        Assert.assertTrue(tokenStorage.exist())
    }


    @Test
    fun saveNotEmptyToken() {
        val response = retrofit2.Response.success(BaseResponse(
            true,
            Response(
                CLIENT_ID,
                CLIENT_UID,
                CLIENT_TARGET_ID
            )
        ))
        val notEmptyToken = tokenExtractor.extract(response)
        tokenStorage.save(notEmptyToken)
        Assert.assertEquals(CLIENT_UID, tokenStorage.load())
        Assert.assertTrue(tokenStorage.exist())
    }
}