package com.qonversion.android.sdk.internal.common.localStorage

interface LocalStorage {

    fun putInt(key: String, value: Int)

    fun getInt(key: String, defValue: Int): Int

    fun putFloat(key: String, value: Float)

    fun getFloat(key: String, defValue: Float): Float

    fun putLong(key: String, value: Long)

    fun getLong(key: String, defValue: Long): Long

    fun putString(key: String, value: String)

    fun getString(key: String, defValue: String): String
}
