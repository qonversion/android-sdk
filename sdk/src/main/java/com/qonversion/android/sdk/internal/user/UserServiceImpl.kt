package com.qonversion.android.sdk.internal.user

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import java.net.HttpURLConnection
import java.util.UUID

private const val TEST_UID = "40egafre6_e_"
private const val USER_ID_PREFIX = "QON"
private const val USER_ID_SEPARATOR = "_"

internal class UserServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: Mapper<User?>,
    private val localStorage: LocalStorage
) : UserService {

    override fun obtainUserId(): String {
        val cachedUserID = localStorage.getString(StorageConstants.UserId.key)
        var resultUserID = cachedUserID

        if (resultUserID.isNullOrEmpty()) {
            resultUserID = localStorage.getString(StorageConstants.Token.key)
            localStorage.remove(StorageConstants.Token.key)
        }

        if (resultUserID.isNullOrEmpty() || resultUserID == TEST_UID) {
            resultUserID = generateRandomUserID()
        }

        if (cachedUserID.isNullOrEmpty() || cachedUserID == TEST_UID) {
            localStorage.putString(StorageConstants.UserId.key, resultUserID)
            localStorage.putString(StorageConstants.OriginalUserId.key, resultUserID)
        }

        return resultUserID
    }

    override fun updateCurrentUserId(id: String) {
        localStorage.putString(StorageConstants.UserId.key, id)
    }

    override fun logoutIfNeeded(): Boolean {
        val originalUserId = localStorage.getString(StorageConstants.OriginalUserId.key, "")
        val defaultUserId = localStorage.getString(StorageConstants.UserId.key, "")

        if (originalUserId == defaultUserId) {
            return false
        }

        localStorage.putString(StorageConstants.UserId.key, originalUserId)

        return true
    }

    override fun resetUser() {
        localStorage.remove(StorageConstants.OriginalUserId.key)
        localStorage.remove(StorageConstants.UserId.key)
        localStorage.remove(StorageConstants.Token.key)
    }

    override suspend fun getUser(id: String): User {
        val request = requestConfigurator.configureUserRequest(id)
        val response = apiInteractor.execute(request)

        return when (response) {
            is Response.Success -> mapUser(response)
            is Response.Error -> {
                if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw QonversionException(
                        ErrorCode.UserNotFound,
                        "Id: $id"
                    )
                }
                throw QonversionException(
                    ErrorCode.BackendError,
                    "Response code ${response.code}, message: ${(response).message}"
                )
            }
        }
    }

    override suspend fun createUser(id: String): User {
        val request = requestConfigurator.configureCreateUserRequest(id)
        val response = apiInteractor.execute(request)

        return when (response) {
            is Response.Success -> mapUser(response)
            is Response.Error -> throw QonversionException(
                ErrorCode.BackendError,
                "Response code ${response.code}, message: ${(response).message}"
            )
        }
    }

    @Throws(QonversionException::class)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun mapUser(response: Response.Success): User {
        return mapper.fromMap(response.mapData) ?: throw QonversionException(ErrorCode.Mapping)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun generateRandomUserID(): String {
        val uuid = UUID.randomUUID().toString().replace(Regex("-"), "")

        return "${USER_ID_PREFIX}${USER_ID_SEPARATOR}$uuid"
    }
}
