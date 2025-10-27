package io.qonversion.nocodes.internal.screen.view

/**
 * Loading states for managing SkeletonView display
 * Exact match with iOS NoCodes SDK logic
 */
enum class LoadingState {
    /**
     * Not loading - initial state
     */
    IDLE,

    /**
     * Loading - show skeleton
     */
    LOADING,

    /**
     * Loaded - hide skeleton, show content
     */
    LOADED,

    /**
     * Error - hide skeleton, show error
     */
    ERROR,

    /**
     * From cache - can immediately hide skeleton
     */
    CACHED
}
