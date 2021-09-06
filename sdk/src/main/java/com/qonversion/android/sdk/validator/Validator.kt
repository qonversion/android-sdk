package com.qonversion.android.sdk.validator

interface Validator<T> {
    fun valid(value: T): Boolean
}
