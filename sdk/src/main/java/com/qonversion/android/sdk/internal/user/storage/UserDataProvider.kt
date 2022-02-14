package com.qonversion.android.sdk.internal.user.storage

import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface UserDataProvider {

    fun getUserId(): String?

    @Throws(QonversionException::class)
    fun requireUserId(): String
}
