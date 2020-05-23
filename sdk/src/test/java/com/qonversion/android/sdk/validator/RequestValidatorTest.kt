package com.qonversion.android.sdk.validator

import com.qonversion.android.sdk.validator.Util.Companion.QONVERSION_REQUEST_WITH_EMPTY_CLIENT_UID
import com.qonversion.android.sdk.validator.Util.Companion.QONVERSION_REQUEST_WITH_NOT_EMPTY_CLIENT_UID
import org.junit.Assert
import org.junit.Test

class RequestValidatorTest {

    @Test
    fun validateWithNotEmptyClientUid() {
        val isValid = RequestValidator().valid(
            QONVERSION_REQUEST_WITH_NOT_EMPTY_CLIENT_UID
        )
        Assert.assertTrue(isValid)
    }

    @Test
    fun validateWithEmptyClientUid() {
        val isValid = RequestValidator().valid(
            QONVERSION_REQUEST_WITH_EMPTY_CLIENT_UID
        )
        Assert.assertFalse(isValid)
    }

}
