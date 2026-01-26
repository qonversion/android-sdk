package io.qonversion.nocodes.internal.screen.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Wrapper for integrating SkeletonView into existing layout
 * Uses View-based implementation
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val skeletonView: NoCodesSkeletonView

    init {
        skeletonView = NoCodesSkeletonView(context)
        addView(skeletonView)

        // Skeleton is hidden by default
        visibility = GONE
    }

    /**
     * Show skeleton
     */
    fun showSkeleton() {
        visibility = VISIBLE
        android.util.Log.d("SkeletonView", "showSkeleton() called")

        // Start animation
        skeletonView.setAnimating(true)
    }

    /**
     * Hide skeleton
     */
    fun hideSkeleton() {
        visibility = GONE
        skeletonView.setAnimating(false)
    }

    /**
     * Check if skeleton is visible
     */
    fun isSkeletonVisible(): Boolean = visibility == VISIBLE

    /**
     * Set dark theme
     */
    fun setDarkTheme(isDark: Boolean) {
        skeletonView.setDarkTheme(isDark)
    }

    /**
     * Enable/disable animation
     */
    fun setAnimating(animating: Boolean) {
        skeletonView.setAnimating(animating)
    }
}
