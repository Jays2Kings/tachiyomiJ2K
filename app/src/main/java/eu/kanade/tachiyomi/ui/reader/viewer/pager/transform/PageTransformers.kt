package eu.kanade.tachiyomi.ui.reader.viewer.pager.transform

import android.view.View
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs
import kotlin.math.max

/**
 * Collection of custom page transformers for the manga reader.
 * Each transformer implements a different animation style for page transitions.
 */

/**
 * Default sliding page transformer (standard ViewPager behavior)
 */
class SlidePageTransformer : ViewPager.PageTransformer {
    override fun transformPage(
        view: View,
        position: Float,
    ) {
        // Default behavior - no custom transformation needed
    }
}

/**
 * Fade transition between pages
 */
class FadePageTransformer : ViewPager.PageTransformer {
    override fun transformPage(
        view: View,
        position: Float,
    ) {
        when {
            position <= -1.0f || position >= 1.0f -> {
                // Page is not visible
                view.alpha = 0f
            }
            position == 0f -> {
                // Page is selected
                view.alpha = 1f
            }
            else -> {
                // Page is in transition
                view.alpha = 1f - abs(position)
            }
        }
    }
}

/**
 * 3D flip effect between pages
 */
class FlipPageTransformer : ViewPager.PageTransformer {
    override fun transformPage(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()

        when {
            position < -1 -> {
                // Page is way off-screen to the left
                view.alpha = 0f
            }
            position <= 0 -> {
                // Use the default slide transition when moving to the left page
                view.alpha = 1f
                view.translationX = 0f
                view.scaleX = 1f
                view.scaleY = 1f
                view.rotationY = 0f
            }
            position <= 1 -> {
                // Rotate the page around its center
                view.alpha = 1f - position
                view.translationX = pageWidth * -position
                view.pivotX = pageWidth * 0.5f
                view.pivotY = pageHeight * 0.5f
                view.rotationY = -90f * position
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
            }
        }
    }
}

/**
 * Depth transition with perspective effect
 */
class DepthPageTransformer : ViewPager.PageTransformer {
    private val minScale = 0.75f

    override fun transformPage(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()

        when {
            position < -1 -> {
                // Page is way off-screen to the left
                view.alpha = 0f
            }
            position <= 0 -> {
                // Use the default slide transition when moving to the left page
                view.alpha = 1f
                view.translationX = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            position <= 1 -> {
                // Fade the page out
                view.alpha = 1f - position

                // Counteract the default slide transition
                view.translationX = pageWidth * -position

                // Scale the page down (between minScale and 1)
                val scaleFactor = minScale + (1 - minScale) * (1 - abs(position))
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
            }
        }
    }
}

/**
 * Zoom in/out transition effect
 */
class ZoomPageTransformer : ViewPager.PageTransformer {
    private val minScale = 0.85f
    private val minAlpha = 0.5f

    override fun transformPage(
        view: View,
        position: Float,
    ) {
        val pageWidth = view.width.toFloat()
        val pageHeight = view.height.toFloat()

        when {
            position < -1 -> {
                // Page is way off-screen to the left
                view.alpha = 0f
            }
            position <= 1 -> {
                // Modify the default slide transition to shrink the page as well
                val scaleFactor = max(minScale, 1 - abs(position))
                val vertMargin = pageHeight * (1 - scaleFactor) / 2
                val horzMargin = pageWidth * (1 - scaleFactor) / 2

                if (position < 0) {
                    view.translationX = horzMargin - vertMargin / 2
                } else {
                    view.translationX = -horzMargin + vertMargin / 2
                }

                // Scale the page down (between minScale and 1)
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor

                // Fade the page relative to its size
                view.alpha = minAlpha + (scaleFactor - minScale) / (1 - minScale) * (1 - minAlpha)
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
            }
        }
    }
}

/**
 * 3D cube rotation effect
 */
class CubePageTransformer : ViewPager.PageTransformer {
    override fun transformPage(
        view: View,
        position: Float,
    ) {
        when {
            position < -1 -> {
                // Page is way off-screen to the left
                view.alpha = 0f
            }
            position <= 0 -> {
                // Left page
                view.alpha = 1f + position
                view.pivotX = view.width.toFloat()
                view.rotationY = 90f * position
            }
            position <= 1 -> {
                // Right page
                view.alpha = 1f - position
                view.pivotX = 0f
                view.rotationY = 90f * position
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
            }
        }
    }
}

/**
 * Accordion-style folding effect
 */
class AccordionPageTransformer : ViewPager.PageTransformer {
    override fun transformPage(
        view: View,
        position: Float,
    ) {
        val absPosition = abs(position)

        when {
            position < -1 -> {
                // Page is way off-screen to the left
                view.alpha = 0f
            }
            position <= 0 -> {
                // Left page
                view.alpha = 1f + position
                view.pivotX = 0f
                view.pivotY = view.height * 0.5f
                view.scaleX = 1f + position
            }
            position <= 1 -> {
                // Right page
                view.alpha = 1f - position
                view.pivotX = view.width.toFloat()
                view.pivotY = view.height * 0.5f
                view.scaleX = 1f - position
            }
            else -> {
                // Page is way off-screen to the right
                view.alpha = 0f
            }
        }
    }
}
