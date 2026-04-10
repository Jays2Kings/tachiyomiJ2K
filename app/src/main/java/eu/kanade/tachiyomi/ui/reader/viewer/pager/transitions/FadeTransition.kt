package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.View
import kotlin.math.abs

/**
 * Fade transition implementation - provides smooth opacity-based transitions between pages.
 * Creates a crossfade effect where pages fade in and out smoothly.
 */
class FadeTransition : AnimatedPageTransition() {
    override val name: String = "fade"
    override val usesCaching: Boolean = true // Can benefit from texture caching

    override fun applyTransform(
        view: View,
        position: Float,
    ) {
        val absPosition = abs(position)

        when {
            position <= -1.0f || position >= 1.0f -> {
                // Page is not visible
                view.alpha = 0f
                view.isClickable = false
            }
            position == 0f -> {
                // Page is fully visible
                view.alpha = 1f
                view.isClickable = true
            }
            else -> {
                // Page is in transition
                view.alpha = 1f - absPosition
                view.isClickable = false

                // Prevent overlapping by adjusting translation slightly
                view.translationX = 0f
                view.translationY = 0f
            }
        }

        // Keep scale consistent
        view.scaleX = 1f
        view.scaleY = 1f
    }

    override fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationStart(fromView, toView)

        // Ensure starting states
        fromView?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }

        toView?.apply {
            alpha = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
    }

    override fun onAnimationEnd(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationEnd(fromView, toView)

        // Ensure final states
        fromView?.apply {
            alpha = 0f
            isClickable = false
        }

        toView?.apply {
            alpha = 1f
            isClickable = true
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
    }

    override fun prepare() {
        super.prepare()
        // Pre-configure for optimal fade performance
        configure(
            duration = 250L,
            easing = EasingFunction.EASE_IN_OUT,
            gpuAccelerated = true,
            caching = true,
        )
    }
}
