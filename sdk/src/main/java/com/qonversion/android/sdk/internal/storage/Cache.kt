package com.qonversion.android.sdk.internal.storage

import com.squareup.moshi.JsonAdapter

internal interface Cache {

    fun putInt(key: String, value: Int)
    /**
     * @param defValue is returned if the Int preference for key does not exist
     */
    fun getInt(key: String, defValue: Int): Int

    fun getBool(key: String, defValue: Boolean = false): Boolean

    fun putBool(key: String, value: Boolean)

    fun putFloat(key: String, value: Float)
    /**
     * @param defValue is returned if the Float preference for key does not exist
     */
    fun getFloat(key: String, defValue: Float): Float

    fun putLong(key: String, value: Long)
    /**
     * @param defValue is returned if the Long preference for key does not exist
     */
    fun getLong(key: String, defValue: Long): Long

    fun putString(key: String, value: String?)
    /**
    * @param defValue is returned if the String preference for key does not exist
    */
    fun getString(key: String, defValue: String?): String?

    fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>)

    fun <T> getObject(key: String, adapter: JsonAdapter<T>): T?

    fun remove(key: String)
}
