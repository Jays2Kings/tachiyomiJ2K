package eu.kanade.tachiyomi.ui.library

import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import kotlinx.android.synthetic.main.filter_bottom_sheet.*
import kotlinx.android.synthetic.main.library_list_controller.*
import kotlin.math.abs

class LibraryGestureDetector(private val controller: LibraryController) : GestureDetector
.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        var result = false
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        if (abs(diffX) <= abs(diffY) &&
            abs(diffY) > MainActivity.SWIPE_THRESHOLD &&
            abs(velocityY) > MainActivity.SWIPE_VELOCITY_THRESHOLD
        ) {
            if (diffY <= 0) {
                controller.showSheet()
            } else {
                controller.filter_bottom_sheet.sheetBehavior?.hide()
            }
            result = true
        } else if (abs(diffX) >= abs(diffY) &&
            abs(diffX) > MainActivity.SWIPE_THRESHOLD * 3 &&
            abs(velocityX) > MainActivity.SWIPE_VELOCITY_THRESHOLD
        ) {
            if (diffX <= 0) {
                controller.category_hopper_frame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    anchorGravity =
                        Gravity.TOP or (
                        if (anchorGravity == Gravity.TOP or Gravity.RIGHT) {
                            controller.preferences.hopperGravity().set(1)
                            Gravity.CENTER
                        } else {
                            controller.preferences.hopperGravity().set(0)
                            Gravity.LEFT
                        }
                        )
                }
            } else {
                controller.category_hopper_frame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    anchorGravity =
                        Gravity.TOP or Gravity.TOP or (
                        if (anchorGravity == Gravity.TOP or Gravity.LEFT) {
                            controller.preferences.hopperGravity().set(1)
                            Gravity.CENTER
                        } else {
                            controller.preferences.hopperGravity().set(2)
                            Gravity.RIGHT
                        }
                        )
                }
            }
            if (!controller.hasMovedHopper) {
                controller.preferences.shownHopperSwipeTutorial().set(true)
            }
            controller.hopperGravity = controller.preferences.hopperGravity().get()
            result = true
        }
        return result
    }
}
