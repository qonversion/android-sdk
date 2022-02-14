package com.qonversion.android.sdk.internal.user.controller

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.user.service.UserService

internal class UserControllerImpl(
    private val userService: UserService,
    private val userCacher: Cacher<User>,
    logger: Logger
) : UserController, BaseClass(logger) {

    override suspend fun getUser(): User {
        logger.verbose("getUser() -> started")

        val cachedUserDefault = userCacher.getActual()
        if (cachedUserDefault != null) {
            return cachedUserDefault
        }
        try {
            val userId = userService.obtainUserId()
            val user = userService.getUser(userId)
            logger.info("User info was successfully received from API")

            storeUser(user)
            return user
        } catch (exception: QonversionException) {
            val cachedUserError =
                userCacher.getActual(CacheState.Error)

            cachedUserError?.let {
                return it
            } ?: run {
                val details = "Failed to retrieve User info"
                logger.error(details, exception)
                throw QonversionException(
                    ErrorCode.BackendError,
                    details,
                    exception
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun storeUser(user: User) {
        try {
            userCacher.store(user)
            logger.info("Cache with user was successfully updated")
        } catch (exception: QonversionException) {
            logger.error("Failed to update cache with User", exception)
        }
    }
}