package com.qonversion.android.sdk.internal.storage

internal interface Storage {

    fun save(token: String)

    fun load(): String

    fun exist(): Boolean

    fun delete()
}
