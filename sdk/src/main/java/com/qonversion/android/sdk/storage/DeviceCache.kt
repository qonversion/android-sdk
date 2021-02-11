package com.qonversion.android.sdk.storage

interface DeviceCache<T, P> {
    fun save(value: T)
    fun load(): P
    fun clear(value: T) = Unit
}