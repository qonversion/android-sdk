package com.qonversion.android.sdk.internal.userProperties.controller

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface UserPropertiesController {

    fun setProperty(key: String, value: String)

    fun setProperties(properties: Map<String, String>)
}
