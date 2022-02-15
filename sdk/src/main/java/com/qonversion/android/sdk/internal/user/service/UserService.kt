package com.qonversion.android.sdk.internal.user.service

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.QonversionException

internal interface UserService {

    fun obtainUserId(): String

    fun updateCurrentUserId(id: String)

    fun logoutIfNeeded(): Boolean

    fun resetUser()

    @Throws(QonversionException::class)
    suspend fun getUser(id: String): User

    @Throws(QonversionException::class)
    suspend fun createUser(id: String): User
}
