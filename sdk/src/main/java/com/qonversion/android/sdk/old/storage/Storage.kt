package com.qonversion.android.sdk.old.storage

interface Storage {

    fun save(token: String)

    fun load(): String

    fun exist(): Boolean

    fun delete()
}
