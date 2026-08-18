package eu.kanade.tachiyomi.ui.library

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.activityBinding
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class LibraryCategoryGestureDetector(
    private val controller: LibraryController?,
) : GestureDetector
        .SimpleOnGestureListener() {
    var locked = false
    var cancelled = false
    private val poa = 1.7f
    private var startingX = 0f
    private var startingY = 0f

    override fun onDown(e: MotionEvent): Boolean {
        locked = false
        startingX = e.x
        startingY = e.y
        controller ?: return false
        val startingOnLibraryView =
            listOfNotNull(
                controller.activityBinding?.bottomNav,
                controller.activityBinding?.sideNav,
                controller.binding.filterBottomSheet.root,
                controller.binding.categoryHopperFrame,
                controller.activityBinding?.appBar,
                controller.visibleHeaderHolder()?.itemView,
            ).none {
                val viewRect = Rect()
                it.getGlobalVisibleRect(viewRect)
                viewRect.contains(e.x.toInt(), e.y.toInt())
            }
        cancelled = !startingOnLibraryView
        return startingOnLibraryView
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        val controller = controller ?: return false
        val distance = startingX - e2.x
        val totalDistanceY = startingY - e2.y
        controller.binding.libraryGridRecycler.recycler.translationX =
            if (!cancelled) abs(distance / 50).pow(poa) * -sign(distance / 50) else 0f
        if (!locked && abs(distance) > 50 && !cancelled) {
            val ev2 = MotionEvent.obtain(e1)
            ev2.action = MotionEvent.ACTION_CANCEL
            controller.binding.swipeRefresh.dispatchTouchEvent(ev2)
            ev2.recycle()
            locked = true
        } else if (abs(totalDistanceY) > 50 && !locked) {
            cancelled = true
            return false
        }
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        locked = false
        if (cancelled) {
            cancelled = false
            return false
        }
        cancelled = false
        val controller = controller ?: return false
        var result = false
        val diffY = e2.y - startingY
        val diffX = e2.x - startingX
        val recycler = controller.binding.libraryGridRecycler.recycler
        var moved = false
        if (abs(diffX) >= abs(diffY) &&
            abs(diffX) > SWIPE_THRESHOLD * 3 &&
            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
            sign(diffX) == sign(velocityX)
        ) {
            moved = controller.jumpToNextCategory((diffX >= 0).xor(controller.binding.root.resources.isLTR))
            result = true
        }
        if (!result || !moved) {
            val animator =
                controller.binding.libraryGridRecycler.recycler
                    .animate()
                    .setDuration(150L)
            animator.translationX(0f)
            animator.withEndAction { recycler.translationX = 0f }
            animator.start()
        }
        return result
    }

    private companion object {
        const val SWIPE_THRESHOLD = 50
        const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
