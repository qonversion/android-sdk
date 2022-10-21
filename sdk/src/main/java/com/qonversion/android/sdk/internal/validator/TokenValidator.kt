package com.qonversion.android.sdk.internal.validator

internal class TokenValidator : Validator<String> {
    override fun valid(value: String): Boolean {
        return value.isNotEmpty()
    }
}
