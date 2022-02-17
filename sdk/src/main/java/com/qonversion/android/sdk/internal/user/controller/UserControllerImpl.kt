package com.qonversion.android.sdk.internal.user.controller

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.appState.AppLifecycleObserver
import com.qonversion.android.sdk.internal.appState.AppStateChangeListener
import com.qonversion.android.sdk.internal.cache.CacheState
import com.qonversion.android.sdk.internal.cache.Cacher
import com.qonversion.android.sdk.internal.common.BaseClass
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.logger.Logger
import com.qonversion.android.sdk.internal.provider.EntitlementsUpdateListenerProvider
import com.qonversion.android.sdk.internal.user.generator.UserIdGenerator
import com.qonversion.android.sdk.internal.user.service.UserService
import com.qonversion.android.sdk.internal.user.storage.UserDataStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEST_UID = "40egafre6_e_"

internal class UserControllerImpl(
    private val userService: UserService,
    private val userCacher: Cacher<User?>,
    private val userDataStorage: UserDataStorage,
    private val entitlementsUpdateListenerProvider: EntitlementsUpdateListenerProvider,
    userIdGenerator: UserIdGenerator,
    appLifecycleObserver: AppLifecycleObserver,
    logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : BaseClass(logger), UserController, AppStateChangeListener {

    init {
        appLifecycleObserver.addListener(this)

        val existingUserId = userDataStorage.getUserId()

        if (existingUserId.isNullOrEmpty() || existingUserId == TEST_UID) {
            val userId = userIdGenerator.generate()
            userDataStorage.setOriginalUserId(userId)
        }
    }

    override suspend fun getUser(): User {
        logger.verbose("getUser() -> started")

        val user = userCacher.getActual() ?: run {
            try {
                val userId = userDataStorage.requireUserId()
                val apiUser = userService.getUser(userId)
                logger.info("User info was successfully received from API")
                storeUser(apiUser)
                return@run apiUser
            } catch (exception: QonversionException) {
                logger.error("Failed to get User from API", exception)
                return@run userCacher.getActual(CacheState.Error)
            }
        }

        return user ?: run {
            logger.error("Failed to retrieve User info")
            throw QonversionException(
                ErrorCode.UserInfoIsMissing
            )
        }
    }

    override fun onAppForeground(isFirst: Boolean) {
        if (!isFirst) {
            return
        }

        scope.launch {
            try {
                if (userCacher.getActual() !== null) {
                    return@launch
                }

                val userId = userDataStorage.requireUserId()
                val newUser = userService.getUser(userId)
                handleNewUserInfo(newUser)
            } catch (exception: QonversionException) {
                logger.error("Requesting user on app first foreground failed", exception)
            }
        }
    }

    @VisibleForTesting
    @Throws(QonversionException::class)
    suspend fun handleNewUserInfo(newUser: User) {
        val currentlyStoredUser = userCacher.get()
        storeUser(newUser)
        if (newUser.entitlements != currentlyStoredUser?.entitlements) {
            withContext(Dispatchers.Main) {
                entitlementsUpdateListenerProvider
                    .entitlementsUpdateListener
                    ?.onEntitlementsUpdated(newUser.entitlements)
            }
        }
    }

    @VisibleForTesting
    fun storeUser(user: User) {
        try {
            userCacher.store(user)
            logger.info("User cache was successfully updated")
        } catch (exception: QonversionException) {
            logger.error("Failed to update user cache", exception)
        }
    }
}
