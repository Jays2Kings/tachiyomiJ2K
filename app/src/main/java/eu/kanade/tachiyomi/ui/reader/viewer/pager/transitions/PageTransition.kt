package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.View

/**
 * Base interface for all page transitions in the manga reader.
 * Defines the common contract for page transition animations.
 */
interface PageTransition {
    /**
     * The name identifier for this transition type
     */
    val name: String

    /**
     * Whether this transition supports GPU acceleration
     */
    val supportsGpuAcceleration: Boolean get() = true

    /**
     * Whether this transition uses texture caching for better performance
     */
    val usesCaching: Boolean get() = false

    /**
     * Called when the transition is applied to a page view
     */
    fun applyTransformation(
        view: View,
        position: Float,
    )

    /**
     * Called when the transition starts
     */
    fun onTransitionStart(
        fromView: View?,
        toView: View?,
    ) {}

    /**
     * Called when the transition completes
     */
    fun onTransitionEnd(
        fromView: View?,
        toView: View?,
    ) {}

    /**
     * Called to prepare the transition (e.g., for caching)
     */
    fun prepare() {}

    /**
     * Called to clean up transition resources
     */
    fun cleanup() {}
}
