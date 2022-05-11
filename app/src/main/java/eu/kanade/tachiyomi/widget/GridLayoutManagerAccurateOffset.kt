package eu.kanade.tachiyomi.widget

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R

class GridLayoutManagerAccurateOffset(context: Context?, spanCount: Int) : GridLayoutManager(context, spanCount) {

    var rView: RecyclerView? = null

    private val toolbarHeight by lazy {
        val attrsArray = intArrayOf(R.attr.mainActionBarSize)
        val array = (context ?: rView?.context)?.obtainStyledAttributes(attrsArray)
        val height = array?.getDimensionPixelSize(0, 0) ?: 0
        array?.recycle()
        height
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        rView = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        rView = null
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        rView ?: return super.computeVerticalScrollOffset(state)
        val firstChild = (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> (pos to getPosition(pos)).takeIf { it.second != RecyclerView.NO_POSITION } }
            .minByOrNull { it.second } ?: return 0
        val scrolledY: Int = -firstChild.first.y.toInt()
        return if (firstChild.second == 0) {
            scrolledY + paddingTop
        } else {
            super.computeVerticalScrollOffset(state)
        }
    }

    override fun findFirstVisibleItemPosition(): Int {
        return getFirstPos(rView, toolbarHeight)
    }

    override fun findFirstCompletelyVisibleItemPosition(): Int {
        return getFirstCompletePos(rView, toolbarHeight)
    }
}
