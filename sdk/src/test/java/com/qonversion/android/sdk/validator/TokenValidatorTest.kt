package com.qonversion.android.sdk.validator

import com.qonversion.android.sdk.storage.Util.Companion.CLIENT_UID
import org.junit.Assert
import org.junit.Test

class TokenValidatorTest {

    @Test
    fun validateWithEmpty() {
        val isValid = TokenValidator().valid("")
        Assert.assertFalse(isValid)
    }

    @Test
    fun validateWithNotNullAndNotEmpty() {
        val isValid = TokenValidator().valid(CLIENT_UID)
        Assert.assertTrue(isValid)
    }

}
