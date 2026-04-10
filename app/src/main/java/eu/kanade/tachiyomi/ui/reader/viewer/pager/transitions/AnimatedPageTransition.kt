package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart

/**
 * Abstract base class for animated page transitions.
 * Provides common functionality for easing, timing, and performance optimization.
 */
abstract class AnimatedPageTransition : PageTransition {
    // Animation configuration
    protected var duration: Long = 300L
    protected var interpolator: Interpolator = DecelerateInterpolator()
    protected var isGpuAccelerated = true
    protected var enableCaching = false

    // Performance monitoring
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var averageFrameTime = 0f

    // Animation state
    private var currentAnimator: AnimatorSet? = null
    private var isAnimating = false

    override val supportsGpuAcceleration: Boolean = true
    override val usesCaching: Boolean get() = enableCaching

    /**
     * Configure the transition with custom settings
     */
    fun configure(
        duration: Long = this.duration,
        easing: EasingFunction = EasingFunction.EASE_OUT,
        gpuAccelerated: Boolean = this.isGpuAccelerated,
        caching: Boolean = this.enableCaching,
    ) {
        this.duration = duration
        this.interpolator = easing.toInterpolator()
        this.isGpuAccelerated = gpuAccelerated
        this.enableCaching = caching
    }

    override fun onTransitionStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onTransitionStart(fromView, toView)

        // Enable GPU acceleration if supported
        if (isGpuAccelerated) {
            fromView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            toView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        isAnimating = true
        frameCount = 0
        lastFrameTime = System.nanoTime()

        onAnimationStart(fromView, toView)
    }

    override fun onTransitionEnd(
        fromView: View?,
        toView: View?,
    ) {
        super.onTransitionEnd(fromView, toView)

        // Restore layer type
        fromView?.setLayerType(View.LAYER_TYPE_NONE, null)
        toView?.setLayerType(View.LAYER_TYPE_NONE, null)

        isAnimating = false
        currentAnimator?.cancel()
        currentAnimator = null

        // Calculate performance metrics
        if (frameCount > 0) {
            averageFrameTime = (System.nanoTime() - lastFrameTime) / frameCount.toFloat() / 1_000_000f
        }

        onAnimationEnd(fromView, toView)
    }

    override fun applyTransformation(
        view: View,
        position: Float,
    ) {
        updatePerformanceMetrics()

        // Apply GPU acceleration if needed
        if (isGpuAccelerated && view.layerType != View.LAYER_TYPE_HARDWARE) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        applyTransform(view, position)
    }

    /**
     * Subclasses implement this to define their specific transformation
     */
    protected abstract fun applyTransform(
        view: View,
        position: Float,
    )

    /**
     * Called when animation starts - subclasses can override for custom setup
     */
    protected open fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {}

    /**
     * Called when animation ends - subclasses can override for custom cleanup
     */
    protected open fun onAnimationEnd(
        fromView: View?,
        toView: View?,
    ) {}

    /**
     * Create a smooth animated transition between views
     */
    protected fun createAnimatedTransition(
        fromView: View?,
        toView: View?,
        animations: AnimatorSet.() -> Unit,
    ) {
        currentAnimator?.cancel()

        currentAnimator =
            AnimatorSet().apply {
                duration = this@AnimatedPageTransition.duration
                interpolator = this@AnimatedPageTransition.interpolator

                doOnStart { onTransitionStart(fromView, toView) }
                doOnEnd { onTransitionEnd(fromView, toView) }

                animations()
                start()
            }
    }

    /**
     * Update performance monitoring metrics
     */
    private fun updatePerformanceMetrics() {
        if (isAnimating) {
            frameCount++
            if (frameCount % 10 == 0) { // Sample every 10 frames
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000f // Convert to ms
                averageFrameTime = (averageFrameTime * 0.9f) + (deltaTime * 0.1f) // Smooth average
                lastFrameTime = currentTime
            }
        }
    }

    /**
     * Get performance metrics for debugging
     */
    fun getPerformanceMetrics(): PerformanceMetrics =
        PerformanceMetrics(
            averageFrameTime = averageFrameTime,
            frameCount = frameCount,
            isDroppingFrames = averageFrameTime > 16.67f, // 60 FPS threshold
            gpuAccelerated = isGpuAccelerated,
        )

    /**
     * Helper function to create smooth alpha transitions
     */
    protected fun View.animateAlpha(
        targetAlpha: Float,
        onComplete: (() -> Unit)? = null,
    ) {
        ObjectAnimator.ofFloat(this, "alpha", alpha, targetAlpha).apply {
            duration = this@AnimatedPageTransition.duration
            interpolator = this@AnimatedPageTransition.interpolator
            doOnEnd { onComplete?.invoke() }
            start()
        }
    }

    /**
     * Helper function to create smooth scale transitions
     */
    protected fun View.animateScale(
        targetScaleX: Float,
        targetScaleY: Float = targetScaleX,
        onComplete: (() -> Unit)? = null,
    ) {
        val scaleXAnimator = ObjectAnimator.ofFloat(this, "scaleX", scaleX, targetScaleX)
        val scaleYAnimator = ObjectAnimator.ofFloat(this, "scaleY", scaleY, targetScaleY)

        AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator)
            duration = this@AnimatedPageTransition.duration
            interpolator = this@AnimatedPageTransition.interpolator
            doOnEnd { onComplete?.invoke() }
            start()
        }
    }

    data class PerformanceMetrics(
        val averageFrameTime: Float,
        val frameCount: Int,
        val isDroppingFrames: Boolean,
        val gpuAccelerated: Boolean,
    )
}
