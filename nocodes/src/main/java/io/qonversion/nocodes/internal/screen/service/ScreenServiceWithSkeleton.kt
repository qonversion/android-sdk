package io.qonversion.nocodes.internal.screen.service

import io.qonversion.nocodes.internal.dto.NoCodeScreen
import io.qonversion.nocodes.internal.screen.view.LoadingState
import io.qonversion.nocodes.internal.screen.view.SkeletonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Расширенный ScreenService с интеграцией SkeletonView
 * Точное соответствие логике iOS NoCodes SDK
 */
internal interface ScreenServiceWithSkeleton : ScreenService {
    
    /**
     * Получить скрин с управлением SkeletonView
     */
    suspend fun getScreenWithSkeleton(
        contextKey: String,
        skeletonManager: SkeletonManager
    ): NoCodeScreen
    
    /**
     * Получить скрин по ID с управлением SkeletonView
     */
    suspend fun getScreenByIdWithSkeleton(
        screenId: String,
        skeletonManager: SkeletonManager
    ): NoCodeScreen
    
    /**
     * Предзагрузить скрины с управлением SkeletonView
     */
    suspend fun preloadScreensWithSkeleton(
        skeletonManager: SkeletonManager
    ): List<NoCodeScreen>
    
    /**
     * Получить состояние загрузки
     */
    fun getLoadingState(): StateFlow<LoadingState>
}

/**
 * Реализация ScreenServiceWithSkeleton
 */
internal class ScreenServiceWithSkeletonImpl(
    private val screenService: ScreenService,
    private val fallbackService: FallbackService?
) : ScreenServiceWithSkeleton {
    
    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    
    override suspend fun getScreen(contextKey: String): NoCodeScreen {
        return screenService.getScreen(contextKey)
    }
    
    override suspend fun getScreenById(screenId: String): NoCodeScreen {
        return screenService.getScreenById(screenId)
    }
    
    override suspend fun preloadScreens(): List<NoCodeScreen> {
        return screenService.preloadScreens()
    }
    
    override suspend fun getScreenWithSkeleton(
        contextKey: String,
        skeletonManager: SkeletonManager
    ): NoCodeScreen {
        return try {
            // Показать скелетон при начале загрузки
            skeletonManager.showSkeleton()
            _loadingState.value = LoadingState.LOADING
            
            // Проверить кэш сначала
            val cachedScreen = (screenService as? ScreenServiceImpl)?.let { service ->
                service.screensByContextKey[contextKey]
            }
            
            if (cachedScreen != null) {
                // Скрин найден в кэше
                skeletonManager.hideSkeletonOnCached()
                _loadingState.value = LoadingState.CACHED
                return cachedScreen
            }
            
            // Загрузить скрин
            val screen = screenService.getScreen(contextKey)
            
            // Успешная загрузка
            skeletonManager.hideSkeletonOnSuccess()
            _loadingState.value = LoadingState.LOADED
            
            screen
        } catch (e: Exception) {
            // Ошибка загрузки
            skeletonManager.hideSkeletonWithError(e.message)
            _loadingState.value = LoadingState.ERROR
            throw e
        }
    }
    
    override suspend fun getScreenByIdWithSkeleton(
        screenId: String,
        skeletonManager: SkeletonManager
    ): NoCodeScreen {
        return try {
            // Показать скелетон при начале загрузки
            skeletonManager.showSkeleton()
            _loadingState.value = LoadingState.LOADING
            
            // Проверить кэш сначала
            val cachedScreen = (screenService as? ScreenServiceImpl)?.let { service ->
                service.screensById[screenId]
            }
            
            if (cachedScreen != null) {
                // Скрин найден в кэше
                skeletonManager.hideSkeletonOnCached()
                _loadingState.value = LoadingState.CACHED
                return cachedScreen
            }
            
            // Загрузить скрин
            val screen = screenService.getScreenById(screenId)
            
            // Успешная загрузка
            skeletonManager.hideSkeletonOnSuccess()
            _loadingState.value = LoadingState.LOADED
            
            screen
        } catch (e: Exception) {
            // Ошибка загрузки
            skeletonManager.hideSkeletonWithError(e.message)
            _loadingState.value = LoadingState.ERROR
            throw e
        }
    }
    
    override suspend fun preloadScreensWithSkeleton(
        skeletonManager: SkeletonManager
    ): List<NoCodeScreen> {
        return try {
            // Показать скелетон при начале предзагрузки
            skeletonManager.showSkeleton()
            _loadingState.value = LoadingState.LOADING
            
            // Предзагрузить скрины
            val screens = screenService.preloadScreens()
            
            if (screens.isNotEmpty()) {
                // Успешная предзагрузка
                skeletonManager.hideSkeletonOnSuccess()
                _loadingState.value = LoadingState.LOADED
            } else {
                // Нет скринов для предзагрузки
                skeletonManager.hideSkeletonOnSuccess()
                _loadingState.value = LoadingState.LOADED
            }
            
            screens
        } catch (e: Exception) {
            // Ошибка предзагрузки
            skeletonManager.hideSkeletonWithError(e.message)
            _loadingState.value = LoadingState.ERROR
            throw e
        }
    }
    
    override fun getLoadingState(): StateFlow<LoadingState> = _loadingState
}

/**
 * Расширение ScreenServiceImpl для доступа к кэшу
 */
internal val ScreenServiceImpl.screensByContextKey: Map<String, NoCodeScreen>
    get() = this.javaClass.getDeclaredField("screensByContextKey").let { field ->
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(this) as Map<String, NoCodeScreen>
    }

internal val ScreenServiceImpl.screensById: Map<String, NoCodeScreen>
    get() = this.javaClass.getDeclaredField("screensById").let { field ->
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(this) as Map<String, NoCodeScreen>
    }

