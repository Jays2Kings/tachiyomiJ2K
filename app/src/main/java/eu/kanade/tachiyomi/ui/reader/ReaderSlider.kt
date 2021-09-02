package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import com.google.android.material.slider.Slider

/**
 * Seekbar to show current chapter progress.
 */
class ReaderSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Slider(context, attrs) {

    /**
     * Whether the seekbar should draw from right to left.
     */
    var isRTL: Boolean
        set(value) {
            layoutDirection = if (value) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
        }
        get() = layoutDirection == LAYOUT_DIRECTION_RTL
    private val boundingBox: Rect = Rect()
    private val exclusions = listOf(boundingBox)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            boundingBox.set(left, top, right, bottom)
            systemGestureExclusionRects = exclusions
        }
    }
}
