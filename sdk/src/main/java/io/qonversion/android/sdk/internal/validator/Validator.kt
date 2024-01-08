package io.qonversion.android.sdk.internal.validator

internal interface Validator<T> {
    fun valid(value: T): Boolean
}
