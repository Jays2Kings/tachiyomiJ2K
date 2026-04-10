package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.View
import kotlin.math.abs

/**
 * Slide transition implementation - provides smooth sliding animations between pages.
 * Supports both horizontal and vertical sliding with customizable direction.
 */
class SlideTransition(
    private val direction: SlideDirection = SlideDirection.HORIZONTAL,
) : AnimatedPageTransition() {
    override val name: String = "slide"
    override val usesCaching: Boolean = false

    enum class SlideDirection {
        HORIZONTAL,
        VERTICAL,
    }

    override fun applyTransform(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()

        when (direction) {
            SlideDirection.HORIZONTAL -> {
                // Standard horizontal slide (default ViewPager behavior)
                view.translationX = 0f
                view.translationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
            }
            SlideDirection.VERTICAL -> {
                // Vertical slide animation
                when {
                    position < -1f -> {
                        // Page is way off-screen above
                        view.alpha = 0f
                        view.translationY = -pageHeight
                    }
                    position <= 1f -> {
                        // Page is visible or transitioning
                        view.alpha = 1f - abs(position * 0.5f)
                        view.translationY = pageHeight * position
                        view.translationX = 0f
                    }
                    else -> {
                        // Page is way off-screen below
                        view.alpha = 0f
                        view.translationY = pageHeight
                    }
                }
            }
        }
    }

    override fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationStart(fromView, toView)

        // Reset any previous transformations
        fromView?.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }

        toView?.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    override fun onAnimationEnd(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationEnd(fromView, toView)

        // Ensure views are in final state
        fromView?.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }

        toView?.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }
}
