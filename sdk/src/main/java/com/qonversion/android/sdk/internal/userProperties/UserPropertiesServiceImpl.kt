package com.qonversion.android.sdk.internal.userProperties

import com.qonversion.android.sdk.internal.common.mappers.UserPropertiesMapper
import com.qonversion.android.sdk.internal.exception.ErrorCode
import com.qonversion.android.sdk.internal.exception.QonversionException
import com.qonversion.android.sdk.internal.networkLayer.apiInteractor.ApiInteractor
import com.qonversion.android.sdk.internal.networkLayer.dto.Response
import com.qonversion.android.sdk.internal.networkLayer.requestConfigurator.RequestConfigurator

internal class UserPropertiesServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: UserPropertiesMapper
) : UserPropertiesService {
    override suspend fun sendProperties(properties: Map<String, String>): List<String> {
        val request = requestConfigurator.configureUserPropertiesRequest(properties)
        val response = apiInteractor.execute(request)

        return when (response) {
            is Response.Success -> mapper.fromMap(response.mapData)
            is Response.Error -> throw QonversionException(
                ErrorCode.BackendError,
                "Response code ${response.code}, message: ${response.message}"
            )
        }
    }
}
