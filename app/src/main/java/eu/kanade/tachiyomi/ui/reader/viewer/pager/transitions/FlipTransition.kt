package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.View

/**
 * Flip transition implementation - provides 3D flip animations between pages.
 * Creates a realistic book page turning effect with rotation around the Y-axis.
 */
class FlipTransition(
    private val flipDirection: FlipDirection = FlipDirection.HORIZONTAL,
) : AnimatedPageTransition() {
    override val name: String = "flip"
    override val usesCaching: Boolean = true

    private val flipAngle = 90f

    enum class FlipDirection {
        HORIZONTAL, // Flip around Y-axis (like a book)
        VERTICAL, // Flip around X-axis (like a calendar)
    }

    override fun applyTransform(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()

        when (flipDirection) {
            FlipDirection.HORIZONTAL -> applyHorizontalFlip(view, position, pageWidth, pageHeight)
            FlipDirection.VERTICAL -> applyVerticalFlip(view, position, pageWidth, pageHeight)
        }
    }

    private fun applyHorizontalFlip(
        view: View,
        position: Float,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        view.pivotX = pageWidth * 0.5f
        view.pivotY = pageHeight * 0.5f

        when {
            position < -1f -> {
                // Page is way off-screen to the left
                view.alpha = 0f
                view.rotationY = -flipAngle
            }
            position <= 0f -> {
                // Left page - rotate from 0° to -90°
                view.alpha = 1f + position // Fade out as it rotates away
                view.rotationY = flipAngle * position
                view.translationX = 0f

                // Add slight scaling for depth effect
                val scale = 1f + position * 0.1f
                view.scaleX = scale
                view.scaleY = scale
            }
            position <= 1f -> {
                // Right page - rotate from 90° to 0°
                view.alpha = 1f - position // Fade in as it rotates into view
                view.rotationY = flipAngle * position
                view.translationX = -pageWidth * position

                // Add slight scaling for depth effect
                val scale = 1f - position * 0.1f
                view.scaleX = scale
                view.scaleY = scale
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
                view.rotationY = flipAngle
            }
        }
    }

    private fun applyVerticalFlip(
        view: View,
        position: Float,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        view.pivotX = pageWidth * 0.5f
        view.pivotY = pageHeight * 0.5f

        when {
            position < -1f -> {
                // Page is way off-screen above
                view.alpha = 0f
                view.rotationX = -flipAngle
            }
            position <= 0f -> {
                // Top page - rotate from 0° to -90°
                view.alpha = 1f + position
                view.rotationX = flipAngle * position
                view.translationY = 0f

                val scale = 1f + position * 0.05f
                view.scaleX = scale
                view.scaleY = scale
            }
            position <= 1f -> {
                // Bottom page - rotate from 90° to 0°
                view.alpha = 1f - position
                view.rotationX = flipAngle * position
                view.translationY = -pageHeight * position

                val scale = 1f - position * 0.05f
                view.scaleX = scale
                view.scaleY = scale
            }
            else -> {
                // Page is way off-screen below
                view.alpha = 0f
                view.rotationX = flipAngle
            }
        }
    }

    override fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationStart(fromView, toView)

        // Set up initial states
        fromView?.apply {
            alpha = 1f
            rotationX = 0f
            rotationY = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f

            // Set pivot points
            pivotX = width / 2f
            pivotY = height / 2f
        }

        toView?.apply {
            alpha = 0f
            rotationX = if (flipDirection == FlipDirection.VERTICAL) flipAngle else 0f
            rotationY = if (flipDirection == FlipDirection.HORIZONTAL) flipAngle else 0f
            scaleX = 0.9f
            scaleY = 0.9f
            translationX = 0f
            translationY = 0f

            // Set pivot points
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
            alpha = 0f
            rotationX = 0f
            rotationY = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }

        toView?.apply {
            alpha = 1f
            rotationX = 0f
            rotationY = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
    }

    override fun prepare() {
        super.prepare()
        // Configure for optimal 3D performance
        configure(
            duration = 400L,
            easing = EasingFunction.EASE_IN_OUT,
            gpuAccelerated = true,
            caching = true,
        )
    }
}
