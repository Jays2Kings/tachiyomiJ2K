package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.graphics.Matrix
import android.graphics.Path
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Page curl transition implementation - simulates realistic page turning with curl effect.
 * Creates an advanced page turning animation that mimics real book pages curling.
 */
class PageCurlTransition : AnimatedPageTransition() {
    override val name: String = "page_curl"
    override val usesCaching: Boolean = true

    // Curl parameters
    private val curlRadius = 50f
    private val curlAngle = 30f
    private val shadowStrength = 0.3f

    override fun applyTransform(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()
        val absPosition = abs(position)

        when {
            position < -1f -> {
                // Page is completely off-screen to the left
                view.alpha = 0f
                resetView(view)
            }
            position <= 0f -> {
                // Left page - apply curl effect
                applyCurlEffect(view, position, pageWidth, pageHeight, isLeftPage = true)
            }
            position <= 1f -> {
                // Right page - coming into view
                applyCurlEffect(view, position, pageWidth, pageHeight, isLeftPage = false)
            }
            else -> {
                // Page is completely off-screen to the right
                view.alpha = 0f
                resetView(view)
            }
        }
    }

    private fun applyCurlEffect(
        view: View,
        position: Float,
        pageWidth: Float,
        pageHeight: Float,
        isLeftPage: Boolean,
    ) {
        val absPosition = abs(position)

        if (isLeftPage) {
            // Left page curls away
            val curlProgress = absPosition
            val curlX = pageWidth * curlProgress
            val curlY = pageHeight * 0.5f

            // Create curl transformation
            view.pivotX = curlX
            view.pivotY = curlY
            view.rotationY = -curlAngle * curlProgress

            // Scale effect for depth
            val scale = 1f - curlProgress * 0.1f
            view.scaleX = scale
            view.scaleY = scale

            // Alpha fade
            view.alpha = 1f - curlProgress * 0.3f

            // Translation for curl effect
            view.translationX = curlProgress * curlRadius
            view.translationZ = -curlProgress * 10f
        } else {
            // Right page uncurls into view
            val uncurlProgress = 1f - absPosition
            val curlX = pageWidth * (1f - absPosition)
            val curlY = pageHeight * 0.5f

            // Create uncurl transformation
            view.pivotX = curlX
            view.pivotY = curlY
            view.rotationY = curlAngle * absPosition

            // Scale effect for depth
            val scale = 0.9f + uncurlProgress * 0.1f
            view.scaleX = scale
            view.scaleY = scale

            // Alpha fade in
            view.alpha = max(0.3f, uncurlProgress)

            // Translation for curl effect
            view.translationX = -absPosition * curlRadius
            view.translationZ = absPosition * 10f
        }

        // Add shadow effect (simulated with overlay)
        addShadowEffect(view, absPosition)
    }

    private fun addShadowEffect(
        view: View,
        progress: Float,
    ) {
        // Simulate shadow by adjusting the view's overlay
        val shadowAlpha = (shadowStrength * progress).coerceIn(0f, shadowStrength)

        // This would typically be done with a custom drawable or overlay
        // For now, we'll use a subtle alpha adjustment
        val currentAlpha = view.alpha
        view.alpha = max(0.1f, currentAlpha - shadowAlpha * 0.2f)
    }

    private fun resetView(view: View) {
        view.rotationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.translationZ = 0f
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
    }

    override fun onAnimationStart(
        fromView: View?,
        toView: View?,
    ) {
        super.onAnimationStart(fromView, toView)

        // Initialize curl effect
        fromView?.apply {
            alpha = 1f
            resetView(this)
        }

        toView?.apply {
            alpha = 0.3f
            resetView(this)
            // Start with curled state
            rotationY = curlAngle
            scaleX = 0.9f
            scaleY = 0.9f
            translationX = -curlRadius
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
            resetView(this)
        }

        toView?.apply {
            alpha = 1f
            resetView(this)
        }
    }

    override fun prepare() {
        super.prepare()
        // Configure for smooth curl animation
        configure(
            duration = 450L,
            easing = EasingFunction.EASE_IN_OUT,
            gpuAccelerated = true,
            caching = true,
        )
    }

    /**
     * Advanced curl effect with bezier curve simulation
     * This creates a more realistic page curl by simulating the curve of a bent page
     */
    private fun createCurlPath(
        width: Float,
        height: Float,
        curlAmount: Float,
    ): Path {
        val path = Path()
        val curlSize = curlAmount * width * 0.3f

        // Create a curved path that simulates page curl
        path.moveTo(0f, 0f)
        path.lineTo(width - curlSize, 0f)

        // Curl curve
        path.quadTo(width, curlSize * 0.5f, width - curlSize * 0.5f, curlSize)
        path.lineTo(curlSize * 0.5f, height - curlSize)
        path.quadTo(0f, height - curlSize * 0.5f, 0f, height)
        path.close()

        return path
    }

    /**
     * Calculate curl transformation matrix
     */
    private fun createCurlMatrix(
        position: Float,
        width: Float,
        height: Float,
    ): Matrix {
        val matrix = Matrix()
        val curlFactor = abs(position)

        // Apply perspective transformation
        val camera = android.graphics.Camera()
        camera.save()
        camera.rotateY(curlAngle * curlFactor)
        camera.getMatrix(matrix)
        camera.restore()

        // Adjust for view center
        matrix.preTranslate(-width / 2f, -height / 2f)
        matrix.postTranslate(width / 2f, height / 2f)

        return matrix
    }
}
