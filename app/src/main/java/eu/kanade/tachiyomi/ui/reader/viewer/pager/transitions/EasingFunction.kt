package eu.kanade.tachiyomi.ui.reader.viewer.pager.transitions

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import kotlin.math.pow

/**
 * Custom easing functions for smooth page transitions.
 * Provides various animation curves commonly used in modern UI design.
 */
enum class EasingFunction {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER,
    ;

    /**
     * Convert the easing function to an Android Interpolator
     */
    fun toInterpolator(): Interpolator =
        when (this) {
            LINEAR -> LinearInterpolator()
            EASE_IN -> AccelerateInterpolator()
            EASE_OUT -> DecelerateInterpolator()
            EASE_IN_OUT -> AccelerateDecelerateInterpolator()
            CUBIC_BEZIER -> CubicBezierInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
        }
}

/**
 * Custom cubic bezier interpolator for advanced easing curves
 */
class CubicBezierInterpolator(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) : Interpolator {
    override fun getInterpolation(input: Float): Float = cubicBezier(input, x1, y1, x2, y2)

    private fun cubicBezier(
        t: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        // Simplified cubic bezier calculation
        val u = 1 - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        var p = uuu * 0f // First control point (0,0)
        p += 3 * uu * t * y1 // Second control point
        p += 3 * u * tt * y2 // Third control point
        p += ttt * 1f // Fourth control point (1,1)

        return p
    }
}

/**
 * Spring-based easing function for natural motion
 */
class SpringInterpolator(
    private val tension: Float = 0.4f,
    private val friction: Float = 0.7f,
) : Interpolator {
    override fun getInterpolation(input: Float): Float {
        val exp = (-tension * input).let { kotlin.math.exp(it.toDouble()).toFloat() }
        val sin = kotlin.math.sin((input * kotlin.math.PI * 2).toDouble()).toFloat()
        return 1f - exp * sin * friction
    }
}

/**
 * Bounce easing function for playful animations
 */
class BounceInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float =
        when {
            input < 1f / 2.75f -> {
                7.5625f * input * input
            }
            input < 2f / 2.75f -> {
                val t = input - 1.5f / 2.75f
                7.5625f * t * t + 0.75f
            }
            input < 2.5f / 2.75f -> {
                val t = input - 2.25f / 2.75f
                7.5625f * t * t + 0.9375f
            }
            else -> {
                val t = input - 2.625f / 2.75f
                7.5625f * t * t + 0.984375f
            }
        }
}

/**
 * Elastic easing function for rubber band effect
 */
class ElasticInterpolator(
    private val amplitude: Float = 1f,
    private val period: Float = 0.3f,
) : Interpolator {
    override fun getInterpolation(input: Float): Float {
        if (input == 0f || input == 1f) return input

        val s = period / 4f
        val t = input - 1
        val exp = amplitude * (2.0.pow(-10.0 * t)).toFloat()
        val sin = kotlin.math.sin((t - s) * (2 * kotlin.math.PI) / period).toFloat()

        return -(exp * sin) + 1f
    }
}
