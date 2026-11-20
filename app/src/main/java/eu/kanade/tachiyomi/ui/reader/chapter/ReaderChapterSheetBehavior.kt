package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

/** Custom BottomSheetBehavior to stop focus from auto-expanding the sheet */
class ReaderChapterSheetBehavior<V : View>
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : BottomSheetBehavior<V>(context, attrs) {
        init {
            isAutoExpandOnRequestChildRectangleOffscreen = false
        }

        override fun onRequestChildRectangleOnScreen(
            parent: CoordinatorLayout,
            child: V,
            rectangle: Rect,
            immediate: Boolean,
        ): Boolean = false
    }
