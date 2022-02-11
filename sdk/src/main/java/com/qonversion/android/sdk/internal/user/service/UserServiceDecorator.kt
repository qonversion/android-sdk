package com.qonversion.android.sdk.internal.user.service

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.user.service.UserService

internal class UserServiceDecorator(
    private val userService: UserService
) : UserService by userService {

    override suspend fun getUser(id: String): User {
        return try {
            userService.getUser(id)
        } catch (exception: QonversionException) {
            if (exception.code == ErrorCode.UserNotFound) {
                userService.createUser(id)
            } else {
                throw exception
            }
        }
    }
}
