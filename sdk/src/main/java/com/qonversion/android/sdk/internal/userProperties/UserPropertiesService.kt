package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface UserPropertiesService {
    @Throws(QonversionException::class)
    suspend fun sendProperties(properties: Map<String, String>): List<String>
}
