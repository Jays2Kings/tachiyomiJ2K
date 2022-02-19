package eu.kanade.tachiyomi.ui.library.display

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
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
        binding.languageBadges.bindToPreference(preferences.languageBadge()) {
            controller?.presenter?.requestLanguageBadgesUpdate()
            val viewGroup = controller?.view as ViewGroup
            val itemsPlay = viewGroup.findViewsById(R.id.play_layout)
            for (item in itemsPlay) {
                val playLayout = item.layoutParams as LayoutParams
                playLayout.topMargin =
                    if (preferences.languageBadge().get()) 25.dpToPx else 0.dpToPx
                item.layoutParams = playLayout
            }
            val itemsLanguageText = viewGroup.findViewsById(R.id.language_text)
            itemsLanguageText.forEach { it.isVisible = preferences.languageBadge().get() }
            val itemsLanguageAngle = viewGroup.findViewsById(R.id.language_angle)
            itemsLanguageAngle.forEach { it.isVisible = preferences.languageBadge().get() }
            val itemsUnread = viewGroup.findViewsById(R.id.unread_text)
            val startPadding = if (preferences.languageBadge().get()) 2.dpToPx else 5.dpToPx
            val endPadding = if (preferences.languageBadge().get()) 8.dpToPx else 5.dpToPx
            itemsUnread.forEach {
                it.updatePaddingRelative(start = startPadding, end = endPadding)
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
