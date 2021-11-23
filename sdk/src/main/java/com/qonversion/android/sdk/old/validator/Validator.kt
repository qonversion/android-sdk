package com.qonversion.android.sdk.old.validator

interface Validator<T> {
    fun valid(value: T): Boolean
}
