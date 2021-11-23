package com.qonversion.android.sdk.old.validator

class TokenValidator : Validator<String> {
    override fun valid(value: String): Boolean {
        return value.isNotEmpty()
    }
}
