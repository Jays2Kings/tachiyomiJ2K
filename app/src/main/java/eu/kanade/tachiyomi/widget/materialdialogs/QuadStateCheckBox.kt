package eu.kanade.tachiyomi.widget.materialdialogs

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.view.setAnimVectorCompat
import eu.kanade.tachiyomi.util.view.setVectorCompat

class QuadStateCheckBox @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    var state: State = State.UNCHECKED

    fun animateDrawableToState(state: State) {
        if (state == this.state) return
        when (state) {
            State.UNCHECKED -> setAnimVectorCompat(
                when (this.state) {
                    State.INVERSED -> R.drawable.anim_check_box_x_to_blank_24dp
                    else -> R.drawable.anim_check_box_checked_to_blank_24dp
                },
                R.attr.colorControlNormal
            )
            State.INDETERMINATE -> setVectorCompat(R.drawable.ic_indeterminate_check_box_24dp, R.attr.colorSecondary)
            State.CHECKED -> setAnimVectorCompat(R.drawable.anim_check_box_blank_to_checked_24dp, R.attr.colorSecondary)
            State.INVERSED -> setAnimVectorCompat(R.drawable.anim_check_box_checked_to_x_24dp, R.attr.colorOnSurface)
        }
        this.state = state
    }

    fun updateDrawable() {
        when (state) {
            State.UNCHECKED -> setVectorCompat(R.drawable.ic_check_box_outline_blank_24dp, R.attr.colorControlNormal)
            State.INDETERMINATE -> setVectorCompat(R.drawable.ic_indeterminate_check_box_24dp, R.attr.colorSecondary)
            State.CHECKED -> setVectorCompat(R.drawable.ic_check_box_24dp, R.attr.colorSecondary)
            State.INVERSED -> setVectorCompat(R.drawable.ic_check_box_x_24dp, R.attr.colorOnSurface)
        }
    }

    enum class State {
        UNCHECKED,
        INDETERMINATE,
        CHECKED,
        INVERSED,
        ;
    }
}
