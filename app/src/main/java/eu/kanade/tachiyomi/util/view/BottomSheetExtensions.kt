package eu.kanade.tachiyomi.util.view

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN

fun BottomSheetBehavior<*>.hide() {
    state = STATE_HIDDEN
}

fun BottomSheetBehavior<*>.collapse() {
    state = STATE_COLLAPSED
}

fun BottomSheetBehavior<*>.expand() {
    state = STATE_EXPANDED
}

fun BottomSheetBehavior<*>?.isExpanded() = this?.state == STATE_EXPANDED
fun BottomSheetBehavior<*>?.isCollapsed() = this?.state == STATE_COLLAPSED
fun BottomSheetBehavior<*>?.isHidden() = this?.state == STATE_HIDDEN
