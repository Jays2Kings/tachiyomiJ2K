package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.isNotGone
import eu.kanade.tachiyomi.util.view.isVisible
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.unread_download_badge.view.*

class LibraryBadge @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    fun setUnreadDownload(unread: Int, downloads: Int, showTotalChapters: Boolean) {
        // Update the unread count and its visibility.

        val unreadBadgeBackground = when (showTotalChapters) {
            true -> context.contextCompatColor(R.color.total_badge)
            false -> context.contextCompatColor(R.color.unread_badge)
        }

        with(unread_text) {
            visibleIf(unread > 0 || unread == -1 || showTotalChapters)

            text = if (unread == -1) "0" else unread.toString()
            setTextColor(
                when (unread == -1 && !showTotalChapters) {
                    true -> context.contextCompatColor(R.color.unread_badge) // hide the 0 in the badge
                    false -> context.contextCompatColor(R.color.unread_badge_text)
                }
            )
            setBackgroundColor(unreadBadgeBackground)
        }

        // Update the download count or local status and its visibility.
        with(download_text) {
            visibleIf(downloads == -2 || downloads > 0)
            text = if (downloads == -2) {
                resources.getString(R.string.local)
            } else {
                downloads.toString()
            }
        }

        // Show the badge card if unread or downloads exists
        this.visibleIf(download_text.isVisible() || unread_text.isNotGone())

        // Show the angles divider if both unread and downloads exists
        unread_angle.visibleIf(download_text.isVisible() && unread_text.isNotGone())

        unread_angle.setColorFilter(unreadBadgeBackground)
        if (unread_angle.isVisible()) {
            download_text.updatePaddingRelative(end = 8.dpToPx)
            unread_text.updatePaddingRelative(start = 2.dpToPx)
        } else {
            download_text.updatePaddingRelative(end = 5.dpToPx)
            unread_text.updatePaddingRelative(start = 5.dpToPx)
        }
    }

    fun setChapters(chapters: Int?) {
        setUnreadDownload(chapters ?: 0, 0, chapters != null)
    }

    fun setInLibrary(inLibrary: Boolean) {
        this.visibleIf(inLibrary)
        unread_angle.gone()
        unread_text.updatePaddingRelative(start = 5.dpToPx)
        unread_text.visibleIf(inLibrary)
        unread_text.text = resources.getText(R.string.in_library)
    }
}
