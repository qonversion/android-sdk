package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.utils.ErrorUtils
import java.net.HttpURLConnection

internal class ScreenServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: Mapper<NoCodeScreen?>,
    private val fallbackService: FallbackService?,
    logger: Logger
) : ScreenService, BaseClass(logger) {

    override suspend fun getScreen(contextKey: String): NoCodeScreen {
        fallbackService?.let { service ->
            try {
                val fallbackScreen = service.loadScreen(contextKey)
                if (fallbackScreen != null) {
                    logger.info("getScreen() -> Successfully loaded fallback screen for context key: $contextKey")
                    return fallbackScreen
                } else {
                    logger.warn("getScreen() -> Fallback screen not found for context key: $contextKey")
                }
            } catch (fallbackError: Exception) {
                logger.error("getScreen() -> Failed to load fallback screen: ${fallbackError.message}")
            }
        } ?: run {
            logger.warn("getScreen() -> Fallback service not available")
        }
//        try {
//            val request = requestConfigurator.configureScreenRequest(contextKey)
//            return when (val response = apiInteractor.execute(request)) {
//                is Response.Success -> {
//                    val arr = response.arrayData
//                    if (arr.isEmpty()) {
//                        throw NoCodesException(
//                            ErrorCode.ScreenNotFound,
//                            "Context key: $contextKey"
//                        )
//                    }
//                    mapper.fromMap(arr[0] as Map<*, *>) ?: throw NoCodesException(ErrorCode.Mapping)
//                }
//                is Response.Error -> {
//                    if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
//                        throw NoCodesException(
//                            ErrorCode.ScreenNotFound,
//                            "Context Key: $contextKey"
//                        )
//                    }
//                    throw NoCodesException(
//                        ErrorCode.BackendError,
//                        "Response code ${response.code}, message: ${(response).message}"
//                    )
//                }
//            }
//        } catch (e: Exception) {
//            // Check if we should trigger fallback
//            if (ErrorUtils.shouldTriggerFallback(e)) {
//                logger.warn("getScreen() -> Network/Server error detected, attempting fallback: ${e.message}")
//
//                fallbackService?.let { service ->
//                    try {
//                        val fallbackScreen = service.loadScreen(contextKey)
//                        if (fallbackScreen != null) {
//                            logger.info("getScreen() -> Successfully loaded fallback screen for context key: $contextKey")
//                            return fallbackScreen
//                        } else {
//                            logger.warn("getScreen() -> Fallback screen not found for context key: $contextKey")
//                        }
//                    } catch (fallbackError: Exception) {
//                        logger.error("getScreen() -> Failed to load fallback screen: ${fallbackError.message}")
//                    }
//                } ?: run {
//                    logger.warn("getScreen() -> Fallback service not available")
//                }
//            }
//
//            // Re-throw the original error if fallback failed or is not available
//            throw e
//        }
        throw NoCodesException(
            ErrorCode.ScreenNotFound,
            "Context key: $contextKey"
        )
    }

    override suspend fun getScreenById(screenId: String): NoCodeScreen {
        fallbackService?.let { service ->
            try {
                val fallbackScreen = service.loadScreenById(screenId)
                if (fallbackScreen != null) {
                    logger.info("getScreenById() -> Successfully loaded fallback screen for screen ID: $screenId")
                    return fallbackScreen
                } else {
                    logger.warn("getScreenById() -> Fallback screen not found for screen ID: $screenId")
                }
            } catch (fallbackError: Exception) {
                logger.error("getScreenById() -> Failed to load fallback screen: ${fallbackError.message}")
            }
        } ?: run {
            logger.warn("getScreenById() -> Fallback service not available")
        }
//        try {
//            val request = requestConfigurator.configureScreenRequestById(screenId)
//            return when (val response = apiInteractor.execute(request)) {
//                is Response.Success -> {
//                    logger.verbose("getScreenById -> mapping the screen from the API")
//                    mapper.fromMap(response.mapData) ?: throw NoCodesException(ErrorCode.Mapping)
//                }
//                is Response.Error -> {
//                    if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
//                        throw NoCodesException(
//                            ErrorCode.ScreenNotFound,
//                            "Screen Id: $screenId"
//                        )
//                    }
//                    throw NoCodesException(
//                        ErrorCode.BackendError,
//                        "Response code ${response.code}, message: ${(response).message}"
//                    )
//                }
//            }
//        } catch (e: Exception) {
//            // Check if we should trigger fallback
//            if (ErrorUtils.shouldTriggerFallback(e)) {
//                logger.warn("getScreenById() -> Network/Server error detected, attempting fallback: ${e.message}")
//
//                fallbackService?.let { service ->
//                    try {
//                        val fallbackScreen = service.loadScreenById(screenId)
//                        if (fallbackScreen != null) {
//                            logger.info("getScreenById() -> Successfully loaded fallback screen for screen ID: $screenId")
//                            return fallbackScreen
//                        } else {
//                            logger.warn("getScreenById() -> Fallback screen not found for screen ID: $screenId")
//                        }
//                    } catch (fallbackError: Exception) {
//                        logger.error("getScreenById() -> Failed to load fallback screen: ${fallbackError.message}")
//                    }
//                } ?: run {
//                    logger.warn("getScreenById() -> Fallback service not available")
//                }
//            }
//
//            // Re-throw the original error if fallback failed or is not available
//            throw e
//        }
        throw NoCodesException(
            ErrorCode.ScreenNotFound,
            "Context key: dasd"
        )
    }
}
