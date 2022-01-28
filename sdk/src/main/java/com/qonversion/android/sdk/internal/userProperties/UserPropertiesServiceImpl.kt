package com.qonversion.android.sdk.internal.userProperties

import androidx.annotation.VisibleForTesting
import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesResponseMapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator

internal class UserPropertiesServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: UserPropertiesResponseMapper
) : UserPropertiesService {
    override suspend fun sendProperties(properties: Map<String, String>): List<String> {
        val request = requestConfigurator.configureUserPropertiesRequest(properties)
        val response = apiInteractor.execute(request)

        return when (response) {
            is Response.Success -> mapProcessedProperties(response)
            is Response.Error -> throw QonversionException(
                ErrorCode.BackendError,
                "Response code ${response.code}, message: ${response.message}"
            )
        }
    }

    @Throws(QonversionException::class)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun mapProcessedProperties(response: Response.Success): List<String> {
        val data = try {
            response.data as Map<*, *>
        } catch (cause: ClassCastException) {
            throw QonversionException(ErrorCode.Mapping, cause = cause)
        }

        return mapper.fromMap(data)
    }
}
