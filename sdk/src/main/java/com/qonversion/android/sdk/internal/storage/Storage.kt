package com.qonversion.android.sdk.internal.storage

internal interface Storage {

    fun load(): String

    fun delete()
}
