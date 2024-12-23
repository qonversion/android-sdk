package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import java.net.HttpURLConnection

internal class ScreenServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: Mapper<NoCodeScreen?>,
) : ScreenService {

    override suspend fun getScreen(screenId: String): NoCodeScreen {
        val request = requestConfigurator.configureScreenRequest(screenId)
        val response = apiInteractor.execute(request)

        return when (response) {
            is Response.Success -> {
                mapper.fromMap(response.mapData) ?: throw NoCodesException(ErrorCode.Mapping)
            }
            is Response.Error -> {
                if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw NoCodesException(
                        ErrorCode.ScreenNotFound,
                        "Id: $screenId"
                    )
                }
                throw NoCodesException(
                    ErrorCode.BackendError,
                    "Response code ${response.code}, message: ${(response).message}"
                )
            }
        }
    }
}