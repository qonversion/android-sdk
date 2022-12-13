package com.qonversion.android.sdk.internal.validator

import com.qonversion.android.sdk.internal.storage.Util.Companion.CLIENT_UID
import org.junit.Assert
import org.junit.Test

internal class TokenValidatorTest {

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
