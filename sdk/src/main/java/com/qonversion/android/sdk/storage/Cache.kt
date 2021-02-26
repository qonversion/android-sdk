package com.qonversion.android.sdk.storage

import com.squareup.moshi.JsonAdapter

interface Cache {

    /**
     * @param key Int preference key
     * @param value Int preference value
     */
    fun putInt(key: String, value: Int)

    /**
     * @param key Int preference key
     * @param defValue Value is returned if the Int preference for key does not exist
     */
    fun getInt(key: String, defValue: Int): Int

    /**
     * @param key Float preference key
     * @param value Float preference value
     */
    fun putFloat(key: String, value: Float)

    /**
     * @param key for Float preference
     * @param defValue Value is returned if the Float preference for key does not exist
     */
    fun getFloat(key: String, defValue: Float): Float

    /**
     * @param key Long preference key
     * @param value Long preference value
     */
    fun putLong(key: String, value: Long)

    /**
     * @param key for Long preference
     * @param defValue Value is returned if the Long preference for key does not exist
     */
    fun getLong(key: String, defValue: Long): Long

    /**
     * @param key String preference key
     * @param value String preference value
     */
    fun putString(key: String, value: String?)

    /**
     * @param key String preference key
     * @param defValue Value is returned if the String preference for key does not exist
     */
    fun getString(key: String, defValue: String?): String?

    /**
     * @param key Object preference key
     * @param value Object preference value
     * @param adapter Adapter for storing an object in memory as a string
     */
    fun <T> putObject(key: String, value: T, adapter: JsonAdapter<T>)

    /**
     * @param key for Object preference
     * @param adapter Adapter for storing an object in memory as a string
     */
    fun <T> getObject(key: String, adapter: JsonAdapter<T>): T?
}