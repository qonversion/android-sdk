package com.qonversion.android.sdk.internal.user

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException

internal class UserServiceDecorator(
    private val userService: UserService
) : UserService by userService {

    override suspend fun getUser(id: String): User {
        return try {
            userService.getUser(id)
        } catch (e: QonversionException) {
            if (e.code == ErrorCode.UserNotFound) {
                userService.createUser(id)
            } else {
                throw e
            }
        }
    }
}
