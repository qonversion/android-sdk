package io.qonversion.nocodes.internal.screen.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Simplified SkeletonView for integration into existing NoCodes architecture
 * Implementation without Compose, using traditional Android Views
 */
class NoCodesSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isDarkTheme: Boolean = false
        private set
    private var isAnimating: Boolean = false
    
    private val skeletonColor = Color.parseColor(SkeletonConstants.SKELETON_COLOR)
    private val backgroundColor = Color.parseColor(SkeletonConstants.BACKGROUND_COLOR_LIGHT)
    private val darkBackgroundColor = Color.parseColor(SkeletonConstants.BACKGROUND_COLOR_DARK)
    
    private val animationValues = listOf(SkeletonConstants.LIGHT_THEME_ALPHA_START, SkeletonConstants.LIGHT_THEME_ALPHA_END)
    private val darkAnimationValues = listOf(SkeletonConstants.DARK_THEME_ALPHA_START, SkeletonConstants.DARK_THEME_ALPHA_END)
    
    private var valueAnimator: ValueAnimator? = null
    private var animationHandler: Handler? = null
    private var animationRunnable: Runnable? = null
    private var currentAlpha: Float = 0.0f
    private var alphaDirection: Float = 0.02f
    private var isAnimationRunning: Boolean = false
    
    init {
        initView()
    }
    
    private fun initView() {
        setBackgroundColor(backgroundColor)
        createSkeletonElements()
        
        postDelayed({
            setAnimating(true)
        }, 100)
    }
    
    private fun createSkeletonElements() {
        // Top layer
        val topView = createSkeletonBox(SkeletonConstants.TOP_LAYER_WIDTH_DP, SkeletonConstants.TOP_LAYER_HEIGHT_DP)
        val topParams = LayoutParams(
            dpToPx(SkeletonConstants.TOP_LAYER_WIDTH_DP), dpToPx(SkeletonConstants.TOP_LAYER_HEIGHT_DP)
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(SkeletonConstants.TOP_LAYER_OFFSET_DP)
        }
        addView(topView, topParams)
        
        // Middle layer
        val screenWidth = resources.displayMetrics.widthPixels
        val midLayerWidth = screenWidth - dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP * 2) // padding on each side
        val midView = createSkeletonBox(midLayerWidth, SkeletonConstants.MID_LAYER_HEIGHT_DP)
        val midParams = LayoutParams(
            midLayerWidth, dpToPx(SkeletonConstants.MID_LAYER_HEIGHT_DP)
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(SkeletonConstants.MID_LAYER_TOP_OFFSET_DP)
        }
        addView(midView, midParams)
        
        // Middle layers (4 rows of 2 elements each)
        val availableWidth = screenWidth - dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP * 2) - dpToPx(SkeletonConstants.MIDDLE_SIZE_LAYERS_BETWEEN_SPACE_DP) // padding + spacing
        val layerWidth = availableWidth / 2
        
        val middleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val middleParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            // Position the block of 8 elements BELOW the large view
            topMargin = dpToPx(SkeletonConstants.MID_LAYER_TOP_OFFSET_DP + SkeletonConstants.MID_LAYER_HEIGHT_DP + SkeletonConstants.MID_CONTAINER_TOP_OFFSET_DP)
            leftMargin = dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP)
            rightMargin = dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP)
        }
        
        repeat(4) { rowIndex ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            repeat(2) { colIndex ->
                val boxView = createSkeletonBox(layerWidth, SkeletonConstants.SMALL_LAYER_HEIGHT_DP)
                val boxParams = LinearLayout.LayoutParams(
                    layerWidth, dpToPx(SkeletonConstants.SMALL_LAYER_HEIGHT_DP)
                ).apply {
                    if (colIndex == 0) {
                        marginEnd = dpToPx(SkeletonConstants.MIDDLE_SIZE_LAYERS_BETWEEN_SPACE_DP)
                    }
                }
                rowLayout.addView(boxView, boxParams)
            }
            
            middleContainer.addView(rowLayout)
            
            if (rowIndex < 3) {
                val spacerHeight = if (rowIndex == 1) SkeletonConstants.MID_CONTAINER_VIEWS_SPACE_DP else SkeletonConstants.MIDDLE_SIZE_LAYERS_TOP_SPACE_DP
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(spacerHeight)
                    )
                }
                middleContainer.addView(spacer)
            }
        }
        
        addView(middleContainer, middleParams)
        
        // Bottom layers (3 elements)
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val bottomParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP)
            leftMargin = dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP)
            rightMargin = dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP)
        }
        
        val botAvailableWidth = screenWidth - dpToPx(SkeletonConstants.DEFAULT_OFFSET_DP * 2) - dpToPx(SkeletonConstants.BOT_LAYER_OFFSET_DP * 2) // padding + 2*8dp spacing
        val botLayerWidth = botAvailableWidth / SkeletonConstants.BOT_LAYERS_COUNT
        
        repeat(SkeletonConstants.BOT_LAYERS_COUNT) { index ->
            val boxView = createSkeletonBox(botLayerWidth, SkeletonConstants.SMALL_LAYER_HEIGHT_DP)
            val boxParams = LinearLayout.LayoutParams(
                botLayerWidth, dpToPx(SkeletonConstants.SMALL_LAYER_HEIGHT_DP)
            ).apply {
                if (index < SkeletonConstants.BOT_LAYERS_COUNT - 1) {
                    marginEnd = dpToPx(SkeletonConstants.BOT_LAYER_OFFSET_DP)
                }
            }
            bottomContainer.addView(boxView, boxParams)
        }
        
        addView(bottomContainer, bottomParams)
    }
    
    private fun createSkeletonBox(width: Int, heightDp: Int): View {
        return View(context).apply {
            setBackgroundColor(skeletonColor)
            alpha = animationValues[0]
            layoutParams = LayoutParams(width, dpToPx(heightDp))
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    fun setDarkTheme(isDark: Boolean) {
        isDarkTheme = isDark
        setBackgroundColor(if (isDark) darkBackgroundColor else backgroundColor)
    }
    
    fun setAnimating(animating: Boolean) {
        isAnimating = animating
        if (animating) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }
    
    private fun startAnimation() {
        if (isAnimationRunning) {
            return
        }
        
        stopAnimation()
        
        isAnimationRunning = true
        animationHandler = Handler(Looper.getMainLooper())
        
        val values = if (isDarkTheme) darkAnimationValues else animationValues
        currentAlpha = values[0]
        
        val alphaRange = values[0] - values[1]
        val totalSteps = 40
        alphaDirection = alphaRange / totalSteps
        
        animationRunnable = object : Runnable {
            override fun run() {
                if (!isAnimationRunning) return
                
                currentAlpha += alphaDirection
                
                if (currentAlpha <= values[1]) {
                    currentAlpha = values[1]
                    alphaDirection = -alphaDirection
                } else if (currentAlpha >= values[0]) {
                    currentAlpha = values[0]
                    alphaDirection = -alphaDirection
                }
                
                post {
                    updateSkeletonAlpha(currentAlpha)
                }
                
                if (isAnimationRunning) {
                    animationHandler?.postDelayed(this, 20)
                }
            }
        }
        
        animationHandler?.post(animationRunnable!!)
    }
    
    private fun stopAnimation() {
        isAnimationRunning = false
        
        animationRunnable?.let { runnable ->
            animationHandler?.removeCallbacks(runnable)
        }
        animationHandler = null
        animationRunnable = null
        
        valueAnimator?.cancel()
        valueAnimator = null
        
        post {
            val values = if (isDarkTheme) darkAnimationValues else animationValues
            updateSkeletonAlpha(values[0])
        }
    }
    
    private fun updateSkeletonAlpha(alpha: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { updateSkeletonAlpha(alpha) }
            return
        }
        
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            updateChildAlpha(child, alpha)
        }
    }
    
    private fun updateChildAlpha(view: View, alpha: Float) {
        if (view is LinearLayout) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                updateChildAlpha(child, alpha)
            }
        } else if (view.background != null) {
            view.alpha = alpha
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        
        if (!isAnimationRunning) {
            startAnimation()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}


