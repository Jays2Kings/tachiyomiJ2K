package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PageTransitionType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions.FlipTransition // Added import
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Central manager for coordinating page transitions in the manga reader.
 * Handles transition selection, performance optimization, and accessibility features.
 */
class TransitionManager(
    private val context: Context,
    private val preferences: SharedPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accessibilityManager = context.getSystemService<AccessibilityManager>()

    // Current transition configuration
    private var currentTransition: PageTransition? = null
    private var transitionType = PageTransitionType.SLIDE
    private var isTransitionsEnabled = true
    private var respectReducedMotion = true

    // Performance tracking
    private val performanceHistory = mutableListOf<PerformanceData>()
    private var isPerformanceOptimizationEnabled = true

    // Gesture handling for transition triggers
    private var gestureDetector: GestureDetector? = null
    private var swipeListener: SwipeListener? = null

    // Callbacks
    var onTransitionStart: ((PageTransition) -> Unit)? = null
    var onTransitionEnd: ((PageTransition) -> Unit)? = null
    var onPerformanceUpdate: ((PerformanceData) -> Unit)? = null

    init {
        loadSettings()
        initializeCurrentTransition()
        setupGestureDetection()
    }

    /**
     * Set the transition type and update the current transition
     */
    fun setTransitionType(type: PageTransitionType) {
        if (transitionType != type) {
            transitionType = type
            initializeCurrentTransition()
            saveSettings()
        }
    }

    /**
     * Enable or disable page transitions
     */
    fun setTransitionsEnabled(enabled: Boolean) {
        if (isTransitionsEnabled != enabled) {
            isTransitionsEnabled = enabled
            if (!enabled) {
                currentTransition = null
            } else {
                initializeCurrentTransition()
            }
            saveSettings()
        }
    }

    /**
     * Apply transition to a view with the given position
     */
    fun applyTransition(
        view: View,
        position: Float,
    ) {
        if (!shouldApplyTransition()) {
            return
        }

        scope.launch {
            try {
                val startTime = System.nanoTime()
                currentTransition?.applyTransformation(view, position)
                val endTime = System.nanoTime()

                // Track performance
                if (isPerformanceOptimizationEnabled) {
                    trackPerformance(endTime - startTime)
                }
            } catch (e: Exception) {
                // Graceful degradation - disable transitions if they cause issues
                handleTransitionError(e)
            }
        }
    }

    /**
     * Start a transition between two views
     */
    fun startTransition(
        fromView: View?,
        toView: View?,
    ) {
        if (!shouldApplyTransition()) {
            return
        }

        currentTransition?.let { transition ->
            transition.onTransitionStart(fromView, toView)
            onTransitionStart?.invoke(transition)

            // Auto-complete transition after duration
            mainHandler.postDelayed({
                endTransition(fromView, toView)
            }, getTransitionDuration())
        }
    }

    /**
     * End the current transition
     */
    fun endTransition(
        fromView: View?,
        toView: View?,
    ) {
        currentTransition?.let { transition ->
            transition.onTransitionEnd(fromView, toView)
            onTransitionEnd?.invoke(transition)
        }
    }

    /**
     * Configure transition settings
     */
    fun configureTransition(
        duration: Long = 300L,
        easing: EasingFunction = EasingFunction.EASE_OUT,
        gpuAcceleration: Boolean = true,
        textureCaching: Boolean = false,
    ) {
        (currentTransition as? AnimatedPageTransition)?.configure(
            duration = duration,
            easing = easing,
            gpuAccelerated = gpuAcceleration,
            caching = textureCaching,
        )
    }

    /**
     * Setup gesture detection for swipe-to-transition
     */
    private fun setupGestureDetection() {
        gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (abs(velocityX) > abs(velocityY) && abs(velocityX) > 1000) {
                            // Horizontal swipe detected
                            swipeListener?.onSwipe(if (velocityX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT)
                            return true
                        }
                        return false
                    }
                },
            )
    }

    /**
     * Handle gesture events for transition triggers
     */
    fun handleGestureEvent(event: MotionEvent): Boolean = gestureDetector?.onTouchEvent(event) ?: false

    /**
     * Set swipe listener for handling gesture-based transitions
     */
    fun setSwipeListener(listener: SwipeListener) {
        this.swipeListener = listener
    }

    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): PerformanceData? =
        (currentTransition as? AnimatedPageTransition)?.getPerformanceMetrics()?.let {
            PerformanceData(
                averageFrameTime = it.averageFrameTime,
                frameCount = it.frameCount,
                isDroppingFrames = it.isDroppingFrames,
                gpuAccelerated = it.gpuAccelerated,
                transitionType = transitionType,
            )
        }

    /**
     * Initialize the current transition based on type
     */
    private fun initializeCurrentTransition() {
        currentTransition?.cleanup()

        if (!isTransitionsEnabled || !shouldApplyTransition()) {
            currentTransition = null
            return
        }

        currentTransition =
            when (transitionType) {
                PageTransitionType.NONE -> null
                PageTransitionType.SLIDE -> SlideTransition()
                PageTransitionType.FADE -> FadeTransition()
                PageTransitionType.ZOOM -> ZoomTransition()
                PageTransitionType.FLIP -> FlipTransition()
                PageTransitionType.DEPTH -> SlideTransition() // Fallback
                PageTransitionType.CUBE -> SlideTransition() // Fallback
                PageTransitionType.ACCORDION -> SlideTransition() // Fallback
            }

        currentTransition?.prepare()
    }

    /**
     * Check if transitions should be applied based on accessibility settings
     */
    private fun shouldApplyTransition(): Boolean {
        if (!isTransitionsEnabled) return false

        // Respect reduced motion accessibility setting
        if (respectReducedMotion && accessibilityManager?.isEnabled == true) {
            // Check if user has reduced motion enabled (requires API 26+)
            return !isReducedMotionEnabled()
        }

        return true
    }

    /**
     * Check if reduced motion is enabled in accessibility settings
     */
    private fun isReducedMotionEnabled(): Boolean =
        try {
            // This would require proper accessibility service integration
            // For now, return false as a safe default
            false
        } catch (e: Exception) {
            false
        }

    /**
     * Track performance metrics
     */
    private fun trackPerformance(executionTime: Long) {
        val performanceData =
            PerformanceData(
                averageFrameTime = executionTime / 1_000_000f, // Convert to ms
                frameCount = 1,
                isDroppingFrames = executionTime > 16_666_667L, // 60 FPS threshold in ns
                gpuAccelerated = true,
                transitionType = transitionType,
            )

        performanceHistory.add(performanceData)

        // Keep only last 100 entries
        if (performanceHistory.size > 100) {
            performanceHistory.removeAt(0)
        }

        onPerformanceUpdate?.invoke(performanceData)

        // Auto-optimize if performance is poor
        if (isPerformanceOptimizationEnabled && performanceData.isDroppingFrames) {
            optimizePerformance()
        }
    }

    /**
     * Handle transition errors gracefully
     */
    private fun handleTransitionError(error: Exception) {
        // Log error and disable problematic transitions
        setTransitionsEnabled(false)

        // Could implement fallback to simpler transitions
        transitionType = PageTransitionType.SLIDE
        initializeCurrentTransition()
    }

    /**
     * Optimize performance by adjusting settings
     */
    private fun optimizePerformance() {
        // Reduce to simpler transition if current one is dropping frames
        when (transitionType) {
            PageTransitionType.FLIP, PageTransitionType.CUBE -> {
                setTransitionType(PageTransitionType.FADE)
            }
            PageTransitionType.FADE, PageTransitionType.ZOOM -> {
                setTransitionType(PageTransitionType.SLIDE)
            }
            else -> {
                // Already at simplest transition, disable if still dropping frames
                setTransitionsEnabled(false)
            }
        }
    }

    /**
     * Get transition duration based on type
     */
    private fun getTransitionDuration(): Long =
        when (transitionType) {
            PageTransitionType.FADE -> 250L
            PageTransitionType.FLIP -> 400L
            PageTransitionType.ZOOM -> 350L
            else -> 300L
        }

    /**
     * Load settings from preferences
     */
    private fun loadSettings() {
        val typeValue = preferences.getInt("page_transition_type", PageTransitionType.SLIDE.value)
        transitionType = PageTransitionType.fromValue(typeValue)
        isTransitionsEnabled = preferences.getBoolean("page_transitions_enabled", true)
        respectReducedMotion = preferences.getBoolean("respect_reduced_motion", true)
        isPerformanceOptimizationEnabled = preferences.getBoolean("performance_optimization", true)
    }

    /**
     * Save settings to preferences
     */
    private fun saveSettings() {
        preferences
            .edit()
            .putInt("page_transition_type", transitionType.value)
            .putBoolean("page_transitions_enabled", isTransitionsEnabled)
            .putBoolean("respect_reduced_motion", respectReducedMotion)
            .putBoolean("performance_optimization", isPerformanceOptimizationEnabled)
            .apply()
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        scope.cancel()
        currentTransition?.cleanup()
        gestureDetector = null
        swipeListener = null
    }

    data class PerformanceData(
        val averageFrameTime: Float,
        val frameCount: Int,
        val isDroppingFrames: Boolean,
        val gpuAccelerated: Boolean,
        val transitionType: PageTransitionType,
    )

    enum class SwipeDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
    }

    interface SwipeListener {
        fun onSwipe(direction: SwipeDirection)
    }
}
