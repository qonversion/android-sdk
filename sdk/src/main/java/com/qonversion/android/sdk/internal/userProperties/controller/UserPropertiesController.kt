package com.qonversion.android.sdk.internal.userProperties.controller

import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlin.jvm.Throws

internal interface UserPropertiesController {

    @Throws(QonversionException::class)
    fun setProperty(key: String, value: String)

    @Throws(QonversionException::class)
    fun setProperties(properties: Map<String, String>)
}
