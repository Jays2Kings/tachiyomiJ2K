package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.fastscroller.FastScroller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPxEnd
import eu.kanade.tachiyomi.util.view.marginTop
import kotlin.math.abs

class MaterialFastScroll @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FastScroller(context, attrs) {

    var canScroll = false
    var startY = 0f
    var scrollOffset = 0
    init {
        setViewsToUse(
            R.layout.material_fastscroll,
            R.id.fast_scroller_bubble,
            R.id.fast_scroller_handle
        )
        autoHideEnabled = true
        ignoreTouchesOutsideHandle = false
        updateScrollListener()
    }

    // Overriding to force a distance moved before scrolling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (recyclerView.computeVerticalScrollRange() <= recyclerView.computeVerticalScrollExtent()) {
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < handle.x - ViewCompat.getPaddingStart(handle)) return false
                val y = event.y
                startY = event.y
                if (canScroll) {
                    handle.isSelected = true
                    notifyScrollStateChange(true)
                    showBubble()
                    showScrollbar()
                    setBubbleAndHandlePosition(y)
                    setRecyclerViewPosition(y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                if (!canScroll && abs(y - startY) > 10) {
                    canScroll = true
                    handle.isSelected = true
                    notifyScrollStateChange(true)
                    showBubble()
                    showScrollbar()
                }
                if (canScroll) {
                    setBubbleAndHandlePosition(y)
                    setRecyclerViewPosition(y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                startY = 0f
                canScroll = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun setBubbleAndHandlePosition(y: Float) {
        super.setBubbleAndHandlePosition(y)
        if (bubbleEnabled) {
            bubble.y = handle.y - bubble.height / 2f + handle.height / 2f
            bubble.translationX = (-45f).dpToPxEnd
        }
    }

    override fun setRecyclerViewPosition(y: Float) {
        if (recyclerView != null) {
            val targetPos = getTargetPos(y)
            if (layoutManager is StaggeredGridLayoutManager) {
                (layoutManager as StaggeredGridLayoutManager).scrollToPositionWithOffset(
                    targetPos,
                    scrollOffset
                )
            } else {
                (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(targetPos, scrollOffset)
            }
            updateBubbleText(targetPos)
        }
    }

    private fun updateScrollListener() {
        onScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isEnabled || bubble == null || handle.isSelected) return
                val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
                val verticalScrollRange = recyclerView.computeVerticalScrollRange() - marginTop
                val proportion =
                    verticalScrollOffset.toFloat() / (verticalScrollRange - height).toFloat()
                setBubbleAndHandlePosition(height * proportion)
                // If scroll amount is small, don't show it
                if (minimumScrollThreshold == 0 || dy == 0 || abs(dy) > minimumScrollThreshold || scrollbarAnimator.isAnimating) {
                    showScrollbar()
                    if (autoHideEnabled) hideScrollbar()
                }
            }
        }
    }
}
