package com.qonversion.android.sdk.storage

interface Storage {

    fun save(token: String)

    fun load(): String

    fun exist(): Boolean

    fun delete()

}