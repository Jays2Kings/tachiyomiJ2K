package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.hide
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class LibraryGestureDetector(private val controller: LibraryController) : GestureDetector
.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        val distance = ((e1?.rawX ?: 0f) - (e2?.rawX ?: 0f)) / 50
        val poa = 1.7f
        controller.binding.categoryHopperFrame.translationX = abs(distance).pow(poa) * -sign(distance)
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return super.onSingleTapUp(e)
    }

    @SuppressLint("RtlHardcoded")
    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        var result = false
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        val hopperFrame = controller.binding.categoryHopperFrame
        val animator = controller.binding.categoryHopperFrame.animate().setDuration(150L)
        animator.translationX(0f)
        animator.withEndAction {
            hopperFrame.translationX = 0f
        }
        if (abs(diffX) <= abs(diffY) &&
            abs(diffY) > SWIPE_THRESHOLD &&
            abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
        ) {
            if (diffY <= 0) {
                controller.showSheet()
            } else {
                controller.binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.hide()
            }
        } else if (abs(diffX) >= abs(diffY) &&
            abs(diffX) > SWIPE_THRESHOLD * 5 &&
            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
        ) {
            val hopperGravity = (controller.binding.categoryHopperFrame.layoutParams as CoordinatorLayout.LayoutParams).gravity
            if (diffX <= 0) {
                animator.translationX(
                    if (hopperGravity == Gravity.TOP or Gravity.LEFT) 0f
                    else (-(controller.view!!.width - controller.binding.categoryHopperFrame.width) / 2).toFloat()
                ).withEndAction {
                    hopperFrame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        gravity =
                            Gravity.TOP or (
                            if (gravity == Gravity.TOP or Gravity.RIGHT) {
                                controller.preferences.hopperGravity().set(1)
                                Gravity.CENTER
                            } else {
                                controller.preferences.hopperGravity().set(0)
                                Gravity.LEFT
                            }
                            )
                    }
                    savePrefs()
                }
            } else {
                animator.translationX(
                    if (hopperGravity == Gravity.TOP or Gravity.RIGHT) 0f
                    else ((controller.view!!.width - hopperFrame.width) / 2).toFloat()
                ).withEndAction {
                    hopperFrame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        gravity =
                            Gravity.TOP or (
                            if (gravity == Gravity.TOP or Gravity.LEFT) {
                                controller.preferences.hopperGravity().set(1)
                                Gravity.CENTER
                            } else {
                                controller.preferences.hopperGravity().set(2)
                                Gravity.RIGHT
                            }
                            )
                    }
                    savePrefs()
                }
            }
            result = true
        }
        animator.start()
        return result
    }

    private fun savePrefs() {
        if (!controller.hasMovedHopper) {
            controller.preferences.shownHopperSwipeTutorial().set(true)
        }
        controller.hopperGravity = controller.preferences.hopperGravity().get()
        controller.binding.categoryHopperFrame.translationX = 0f
    }

    private companion object {
        const val SWIPE_THRESHOLD = 50
        const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
