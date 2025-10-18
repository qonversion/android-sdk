package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.error.ErrorCode
import io.qonversion.nocodes.error.NoCodesException
import io.qonversion.nocodes.internal.common.BaseClass
import io.qonversion.nocodes.internal.common.mappers.Mapper
import io.qonversion.nocodes.internal.networkLayer.apiInteractor.ApiInteractor
import io.qonversion.nocodes.internal.networkLayer.requestConfigurator.RequestConfigurator
import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.logger.Logger
import io.qonversion.nocodes.internal.networkLayer.dto.Response
import io.qonversion.nocodes.internal.networkLayer.dto.Request
import io.qonversion.nocodes.internal.utils.ErrorUtils
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap

internal class ScreenServiceImpl(
    private val requestConfigurator: RequestConfigurator,
    private val apiInteractor: ApiInteractor,
    private val mapper: Mapper<NoCodeScreen?>,
    private val fallbackService: FallbackService?,
    logger: Logger
) : ScreenService, BaseClass(logger) {

    private val screensById = ConcurrentHashMap<String, NoCodeScreen>()
    private val screensByContextKey = ConcurrentHashMap<String, NoCodeScreen>()

    override suspend fun getScreen(contextKey: String): NoCodeScreen {
        // Check cache first
        screensByContextKey[contextKey]?.let { cachedScreen ->
            logger.verbose("getScreen() -> Screen found in cache for context key: $contextKey")
            return cachedScreen
        }

        return executeWithFallback(
            requestProvider = { requestConfigurator.configureScreenRequest(contextKey) },
            fallbackProvider = { fallbackService?.loadScreen(contextKey) },
            errorContext = "context key: $contextKey",
            methodName = "getScreen"
        )
    }

    override suspend fun getScreenById(screenId: String): NoCodeScreen {
        // Check cache first
        screensById[screenId]?.let { cachedScreen ->
            logger.verbose("getScreenById() -> Screen found in cache for screen id: $screenId")
            return cachedScreen
        }

        return executeWithFallback(
            requestProvider = { requestConfigurator.configureScreenRequestById(screenId) },
            fallbackProvider = { fallbackService?.loadScreenById(screenId) },
            errorContext = "screen id: $screenId",
            methodName = "getScreenById"
        )
    }

    override suspend fun preloadScreens(): List<NoCodeScreen> {
        return try {
            val request = requestConfigurator.configurePreloadScreensRequest()
            when (val response = apiInteractor.execute(request)) {
                is Response.Success -> {
                    val arrayData = response.arrayData
                    if (arrayData.isNullOrEmpty()) {
                        logger.info("preloadScreens() -> No screens to preload")
                        return emptyList()
                    }

                    val screens = arrayData.mapNotNull { data ->
                        mapper.fromMap(data as Map<*, *>)
                    }

                    if (screens.isNotEmpty()) {
                        screens.forEach { screen ->
                            screensById[screen.id] = screen
                            screensByContextKey[screen.contextKey] = screen
                        }
                        logger.info("preloadScreens() -> Successfully preloaded ${screens.size} screens")
                    }

                    screens
                }
                is Response.Error -> {
                    logger.warn("preloadScreens() -> Failed to preload screens: ${response.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("preloadScreens() -> Error during preload: ${e.message}")
            emptyList()
        }
    }

    private suspend fun executeWithFallback(
        requestProvider: () -> Request,
        fallbackProvider: suspend () -> NoCodeScreen?,
        errorContext: String,
        methodName: String
    ): NoCodeScreen {
        try {
            val request = requestProvider()
            return when (val response = apiInteractor.execute(request)) {
                is Response.Success -> {
                    val arrayData = response.arrayData
                    val mapData = response.mapData

                    // Check if response has any data
                    if (arrayData.isNullOrEmpty() && mapData.isNullOrEmpty()) {
                        throw NoCodesException(ErrorCode.ScreenNotFound, errorContext)
                    }

                    val screen = when {
                        arrayData?.isNotEmpty() == true -> {
                            // Handle array response (for context key requests)
                            mapper.fromMap(arrayData[0] as Map<*, *>)
                        }
                        mapData?.isNotEmpty() == true -> {
                            // Handle map response (for screen ID requests)
                            logger.verbose("$methodName -> mapping the screen from the API")
                            mapper.fromMap(mapData)
                        }
                        else -> null
                    }

                    screen ?: throw NoCodesException(ErrorCode.Mapping)

                    // Cache successfully loaded screen
                    screensById[screen.id] = screen
                    screensByContextKey[screen.contextKey] = screen

                    screen
                }
                is Response.Error -> {
                    if (response.code == HttpURLConnection.HTTP_NOT_FOUND) {
                        throw NoCodesException(ErrorCode.ScreenNotFound, errorContext)
                    }
                    throw NoCodesException(
                        ErrorCode.BackendError,
                        "Response code ${response.code}, message: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            // Check if we should trigger fallback
            if (ErrorUtils.shouldTriggerFallback(e)) {
                logger.warn("$methodName() -> Network/Server error detected, attempting fallback: ${e.message}")

                fallbackService?.let { service ->
                    try {
                        val fallbackScreen = fallbackProvider()
                        if (fallbackScreen != null) {
                            logger.info("$methodName() -> Successfully loaded fallback screen for $errorContext")
                            return fallbackScreen
                        } else {
                            logger.warn("$methodName() -> Fallback screen not found for $errorContext")
                        }
                    } catch (fallbackError: Exception) {
                        logger.error("$methodName() -> Failed to load fallback screen: ${fallbackError.message}")
                    }
                } ?: run {
                    logger.warn("$methodName() -> Fallback service not available")
                }
            }

            // Re-throw the original error if fallback failed or is not available
            throw e
        }
    }
}
