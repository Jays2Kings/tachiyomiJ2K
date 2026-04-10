package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom transition implementation - provides smooth scale-based transitions between pages.
 * Pages zoom in/out during transitions creating a depth effect.
 */
class ZoomTransition(
    private val zoomType: ZoomType = ZoomType.ZOOM_IN,
) : AnimatedPageTransition() {
    override val name: String = "zoom"
    override val usesCaching: Boolean = true

    private val minScale = 0.75f
    private val maxScale = 1.25f
    private val minAlpha = 0.5f

    enum class ZoomType {
        ZOOM_IN, // Pages zoom in during transition
        ZOOM_OUT, // Pages zoom out during transition
        ZOOM_BOTH, // Combination zoom effect
    }

    override fun applyTransform(
        view: View,
        position: Float,
    ) {
        val absPosition = abs(position)
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()

        when {
            position < -1f -> {
                // Page is way off-screen to the left
                view.alpha = 0f
                view.scaleX = minScale
                view.scaleY = minScale
            }
            position <= 1f -> {
                applyZoomEffect(view, position, absPosition, pageWidth, pageHeight)
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
                view.scaleX = minScale
                view.scaleY = minScale
            }
        }
    }

    private fun applyZoomEffect(
        view: View,
        position: Float,
        absPosition: Float,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        when (zoomType) {
            ZoomType.ZOOM_IN -> {
                // Scale factor increases as page becomes visible
                val scaleFactor = max(minScale, 1f - absPosition * 0.25f)
                val alpha = max(minAlpha, 1f - absPosition * 0.5f)

                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                view.alpha = alpha

                // Center the scaled page
                val vertMargin = pageHeight * (1f - scaleFactor) / 2f
                val horzMargin = pageWidth * (1f - scaleFactor) / 2f

                if (position < 0) {
                    view.translationX = horzMargin - vertMargin / 2f
                } else {
                    view.translationX = -horzMargin + vertMargin / 2f
                }
            }

            ZoomType.ZOOM_OUT -> {
                // Scale factor decreases as page becomes visible
                val scaleFactor = min(maxScale, 1f + absPosition * 0.25f)
                val alpha = max(minAlpha, 1f - absPosition * 0.3f)

                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                view.alpha = alpha

                // Center the scaled page
                view.translationX = 0f
                view.translationY = 0f
            }

            ZoomType.ZOOM_BOTH -> {
                // Combination effect
                val scaleFactor =
                    if (position < 0) {
                        // Left page zooms out
                        min(maxScale, 1f + absPosition * 0.3f)
                    } else {
                        // Right page zooms in
                        max(minScale, 1f - absPosition * 0.3f)
                    }

                val alpha = max(minAlpha, 1f - absPosition * 0.4f)

                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                view.alpha = alpha

                // Slight translation for depth effect
                view.translationX = position * pageWidth * 0.1f
            }
        }
    }

    override fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationStart(fromView, toView)

        // Initialize views for zoom transition
        fromView?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            translationX = 0f
            translationY = 0f
            pivotX = width / 2f
            pivotY = height / 2f
        }

        toView?.apply {
            scaleX =
                when (zoomType) {
                    ZoomType.ZOOM_IN -> minScale
                    ZoomType.ZOOM_OUT -> maxScale
                    ZoomType.ZOOM_BOTH -> minScale
                }
            scaleY = scaleX
            alpha = minAlpha
            translationX = 0f
            translationY = 0f
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    override fun onAnimationEnd(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationEnd(fromView, toView)

        // Restore normal state
        fromView?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 0f
            translationX = 0f
            translationY = 0f
        }

        toView?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            translationX = 0f
            translationY = 0f
        }
    }

    override fun prepare() {
        super.prepare()
        // Configure for optimal zoom performance
        configure(
            duration = 350L,
            easing = EasingFunction.EASE_OUT,
            gpuAccelerated = true,
            caching = true,
        )
    }
}
