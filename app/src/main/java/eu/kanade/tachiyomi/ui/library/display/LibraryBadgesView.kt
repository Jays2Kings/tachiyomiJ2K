package eu.kanade.tachiyomi.ui.library.display

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.LibraryBadgesLayoutBinding
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.BaseLibraryDisplayView

class LibraryBadgesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseLibraryDisplayView<LibraryBadgesLayoutBinding>(context, attrs) {

    override fun inflateBinding() = LibraryBadgesLayoutBinding.bind(this)
    override fun initGeneralPreferences() {
        binding.unreadBadgeGroup.bindToPreference(preferences.unreadBadgeType()) {
            controller?.presenter?.requestUnreadBadgesUpdate()
        }
        binding.hideReading.bindToPreference(preferences.hideStartReadingButton())
        binding.languageBadges.bindToPreference(preferences.languageBadge()) { languageBadge ->
            controller?.presenter?.requestLanguageBadgesUpdate()
            val isCompact = preferences.libraryLayout().get() == 1
            if (isCompact) {
                val viewGroup = controller?.view as ViewGroup
                val itemsPlay = viewGroup.findViewsById(R.id.play_layout)
                for (item in itemsPlay) {
                    val itemLayout = item.layoutParams as LayoutParams
                    itemLayout.topMargin = if (languageBadge) 25.dpToPx else 0.dpToPx
                    item.layoutParams = itemLayout
                }
            }
        }
        binding.downloadBadge.bindToPreference(preferences.downloadBadge()) {
            controller?.presenter?.requestDownloadBadgesUpdate()
        }
        binding.showNumberOfItems.bindToPreference(preferences.categoryNumberOfItems())
    }

    private fun ViewGroup.findViewsById(id: Int): ArrayList<View> = ArrayList<View>().also {
        this.children.forEach { child ->
            if (child is ViewGroup) it.addAll(child.findViewsById(id))
            if (child.id == id) it.add(child)
        }
    }
}
