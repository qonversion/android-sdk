package io.qonversion.nocodes.internal.screen.view

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager for controlling SkeletonView show/hide
 * Simple implementation without Compose dependencies
 */
class SkeletonManager {

    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _isSkeletonVisible = MutableStateFlow(false)
    val isSkeletonVisible: StateFlow<Boolean> = _isSkeletonVisible.asStateFlow()

    private val _isAnimating = MutableStateFlow(false)
    val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()

    private val isInitialized = AtomicBoolean(false)

    /**
     * Show skeleton at the start of loading
     */
    fun showSkeleton() {
        if (_loadingState.value == LoadingState.LOADING) {
            return // Already showing
        }

        _loadingState.value = LoadingState.LOADING
        _isSkeletonVisible.value = true

        // Animation starts immediately
        _isAnimating.value = true

        isInitialized.set(true)
    }

    /**
     * Hide skeleton on successful loading
     */
    fun hideSkeletonOnSuccess() {
        if (_loadingState.value != LoadingState.LOADING) {
            return
        }

        _loadingState.value = LoadingState.LOADED
        hideSkeletonInternal()
    }

    /**
     * Hide skeleton on cached loading
     */
    fun hideSkeletonOnCached() {
        if (_loadingState.value != LoadingState.LOADING) {
            return
        }

        _loadingState.value = LoadingState.CACHED
        hideSkeletonInternal()
    }

    /**
     * Hide skeleton on error
     */
    fun hideSkeletonWithError(errorMessage: String? = null) {
        if (_loadingState.value != LoadingState.LOADING) {
            return
        }

        _loadingState.value = LoadingState.ERROR
        hideSkeletonInternal()

        // Error logging can be added here
        errorMessage?.let {
            // Logger.error("SkeletonView hidden due to error: $errorMessage")
        }
    }

    /**
     * Internal method for hiding skeleton
     */
    private fun hideSkeletonInternal() {
        // Stop animation
        _isAnimating.value = false

        // Hide skeleton
        _isSkeletonVisible.value = false
    }

    /**
     * Reset manager state
     */
    fun reset() {
        hideSkeletonInternal()
        _loadingState.value = LoadingState.IDLE
        isInitialized.set(false)
    }

    /**
     * Check if skeleton should be shown
     */
    fun shouldShowSkeleton(): Boolean {
        return _loadingState.value == LoadingState.LOADING && _isSkeletonVisible.value
    }

    /**
     * Check if skeleton should be hidden
     */
    fun shouldHideSkeleton(): Boolean {
        return _loadingState.value in listOf(LoadingState.LOADED, LoadingState.ERROR, LoadingState.CACHED)
    }

    /**
     * Get current loading state
     */
    fun getCurrentState(): LoadingState = _loadingState.value

    /**
     * Check if manager is initialized
     */
    fun isInitialized(): Boolean = isInitialized.get()
}
