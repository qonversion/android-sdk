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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : BaseClass(logger), UserController, AppStateChangeListener {

    @VisibleForTesting
    val userRequestMutex = Mutex()

    init {
        appLifecycleObserver.subscribeOnAppStateChanges(this)
    }

    init {
        val existingUserId = userDataStorage.getUserId()

        if (existingUserId.isNullOrEmpty() || existingUserId == TEST_UID) {
            val userId = userIdGenerator.generate()
            userDataStorage.setOriginalUserId(userId)
        }
    }

    override suspend fun getUser(): User {
        logger.verbose("getUser() -> started")

        val user = userCacher.getActual() ?: userRequestMutex.withLock {
            try {
                val userId = userDataStorage.requireUserId()
                val apiUser = userService.getUser(userId)
                logger.info("User info was successfully received from API")
                storeUser(apiUser)
                return@withLock apiUser
            } catch (exception: QonversionException) {
                logger.error("Failed to get User from API", exception)
                return@withLock userCacher.getActual(CacheState.Error)
            }
        }

        return user ?: run {
            logger.error("Failed to retrieve User info")
            throw QonversionException(
                ErrorCode.UserInfoIsMissing
            )
        }
    }

    override fun onAppFirstForeground() {
        if (!userRequestMutex.isLocked) {
            scope.launch {
                if (userCacher.getActual() !== null) {
                    return@launch
                }

                val currentlyStoredUser = userCacher.get()
                val newUser = try {
                    getUser()
                } catch (exception: QonversionException) {
                    null
                }

                newUser?.let {
                    if (it.entitlements != currentlyStoredUser?.entitlements) {
                        withContext(Dispatchers.Main) {
                            entitlementsUpdateListenerProvider
                                .entitlementsUpdateListener
                                ?.onEntitlementsUpdated(it.entitlements)
                        }
                    }
                }
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
