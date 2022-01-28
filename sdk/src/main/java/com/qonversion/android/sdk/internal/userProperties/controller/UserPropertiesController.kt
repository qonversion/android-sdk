package com.qonversion.android.sdk.internal.userProperties.controller

internal interface UserPropertiesController {

    fun setProperty(key: String, value: String)

    fun setProperties(properties: Map<String, String>)
}
