package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import eu.kanade.tachiyomi.util.system.pxToDp
import kotlin.math.max
import kotlin.math.roundToInt

class AutofitRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    androidx.recyclerview.widget.RecyclerView(context, attrs) {

    val manager = GridLayoutManager(context, 1)

    var columnWidth = -1f
        set(value) {
            field = value
            if (measuredWidth > 0)
                setSpan(true)
        }

    var spanCount = 0
        set(value) {
            field = value
            if (value > 0) {
                manager.spanCount = value
            }
        }

    val itemWidth: Int
        get() {
            return if (spanCount == 0) measuredWidth / getTempSpan()
            else measuredWidth / manager.spanCount
        }

    init {
        layoutManager = manager
    }

    private fun getTempSpan(): Int {
        if (spanCount == 0 && columnWidth > 0) {
            val dpWidth = (measuredWidth.pxToDp / 100f).roundToInt()
            return max(1, (dpWidth / columnWidth).roundToInt())
        }
        return 3
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        setSpan()
    }

    private fun setSpan(force: Boolean = false) {
        if ((spanCount == 0 || force) && columnWidth > 0) {
            val dpWidth = (measuredWidth.pxToDp / 100f).roundToInt()
            val count = max(1, (dpWidth / columnWidth).roundToInt())
            spanCount = count
        }
    }
}
