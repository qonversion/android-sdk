package com.qonversion.android.sdk.internal.user.controller

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.QonversionException

interface UserController {

    @Throws(QonversionException::class)
    suspend fun getUser(): User
}
