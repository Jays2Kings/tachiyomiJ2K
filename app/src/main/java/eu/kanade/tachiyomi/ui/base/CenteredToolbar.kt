package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import com.google.android.material.appbar.MaterialToolbar
import eu.kanade.tachiyomi.R
import kotlinx.android.synthetic.main.main_activity.view.*

class CenteredToolbar@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialToolbar(context, attrs) {

    override fun setTitle(resId: Int) {
        if (navigationIcon is DrawerArrowDrawable) {
            super.setTitle(resId)
            toolbar_title.text = null
            hideDropdown()
        } else {
            toolbar_title.text = context.getString(resId)
            super.setTitle(null)
        }
    }

    override fun setTitle(title: CharSequence?) {
        if (navigationIcon is DrawerArrowDrawable) {
            super.setTitle(title)
            toolbar_title.text = ""
            hideDropdown()
        } else {
            toolbar_title.text = title
            super.setTitle(null)
        }
    }

    fun showDropdown(down: Boolean = true) {
        toolbar_title.setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.ic_blank_24dp,
            0,
            if (down) {
                R.drawable.ic_arrow_drop_down_24dp
            } else {
                R.drawable.ic_arrow_drop_up_24dp
            },
            0
        )
    }

    fun hideDropdown() {
        toolbar_title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }
}
