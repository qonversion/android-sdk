package com.qonversion.android.sdk.internal.user

import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.common.StorageConstants
import com.qonversion.android.sdk.internal.common.localStorage.LocalStorage
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.serializers.mappers.UserMapper
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
    private val mapper: UserMapper,
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

        if (response !is Response.Success) {
            if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
                return createUser(id)
            }
            throw QonversionException(ErrorCode.BadResponse, "Response code: ${response.code}")
        }
        return mapUser(response)
    }

    override suspend fun createUser(id: String): User {
        val request = requestConfigurator.configureCreateUserRequest(id)
        val response = apiInteractor.execute(request)

        if (response !is Response.Success) {
            throw QonversionException(ErrorCode.BadResponse, "Response code: ${response.code}")
        }
        return mapUser(response)
    }

    @Throws(QonversionException::class)
    private fun mapUser(response: Response.Success): User {
        val data = try {
            response.data as Map<*, *>
        } catch (cause: ClassCastException) {
            throw QonversionException(ErrorCode.Mapping, cause = cause)
        }
        return mapper.fromMap(data) ?: throw QonversionException(ErrorCode.Mapping)
    }

    private fun generateRandomUserID(): String {
        val uuid = UUID.randomUUID().toString().replace(Regex("-"), "")

        return "${USER_ID_PREFIX}${USER_ID_SEPARATOR}$uuid"
    }
}
