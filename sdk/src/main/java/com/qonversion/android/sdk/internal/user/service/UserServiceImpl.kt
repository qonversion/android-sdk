package com.qonversion.android.sdk.internal.user.service

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.dto.User
import com.qonversion.android.sdk.internal.common.mappers.Mapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator
import java.net.HttpURLConnection

internal class UserServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: Mapper<User?>,
) : UserService {

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
}