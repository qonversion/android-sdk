package com.qonversion.android.sdk.internal.user.service

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import kotlinx.coroutines.CompletableDeferred

internal class UserServiceDecorator(
    private val userService: UserService
) : UserService by userService {

    @VisibleForTesting
    var userLoadingDeferred: CompletableDeferred<User>? = null

    override suspend fun getUser(id: String): User {
        return userLoadingDeferred?.await() ?: run {
            userLoadingDeferred = CompletableDeferred()

            val user = loadOrCreateUser(id)

            userLoadingDeferred?.complete(user)
            userLoadingDeferred = null

            return@run user
        }
    }

    @VisibleForTesting
    suspend fun loadOrCreateUser(id: String): User {
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
