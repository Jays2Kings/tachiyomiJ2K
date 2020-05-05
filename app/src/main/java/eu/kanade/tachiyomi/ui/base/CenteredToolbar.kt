package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import com.google.android.material.appbar.MaterialToolbar
import eu.kanade.tachiyomi.util.view.gone
import kotlinx.android.synthetic.main.main_activity.view.*

class CenteredToolbar@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialToolbar(context, attrs) {

    override fun setTitle(resId: Int) {
        if (navigationIcon is DrawerArrowDrawable) {
            super.setTitle(resId)
            toolbar_title.text = null
            dropdown?.gone()
        } else {
            toolbar_title.text = context.getString(resId)
            super.setTitle(null)
        }
    }

    override fun setTitle(title: CharSequence?) {
        if (navigationIcon is DrawerArrowDrawable) {
            super.setTitle(title)
            toolbar_title.text = ""
            dropdown?.gone()
        } else {
            toolbar_title.text = title
            super.setTitle(null)
        }
    }
}
