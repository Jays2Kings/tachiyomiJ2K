package eu.kanade.tachiyomi.ui.library

import android.view.View
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.manga_grid_item.*

/**
 * Generic class used to hold the displayed data of a manga in the library.
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to the single tap and long tap events.
 */

abstract class LibraryHolder(
    view: View,
    val adapter: LibraryCategoryAdapter
) : BaseFlexibleViewHolder(view, adapter) {

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    abstract fun onSetValues(item: LibraryItem)

    fun setUnreadBadge(badge: LibraryBadge, item: LibraryItem) {
        val showTotal = item.header.category.sortingMode() == LibrarySort.TOTAL
        badge.setUnreadDownload(
            when {
                showTotal -> item.manga.totalChapters
                item.unreadType == 2 -> item.manga.unread
                item.unreadType == 1 -> if (item.manga.unread > 0) -1 else -2
                else -> -2
            },
            when {
                item.downloadCount == -1 -> -1
                item.manga.source == LocalSource.ID -> -2
                else -> item.downloadCount
            },
            showTotal
        )
    }

    fun setReadingButton(item: LibraryItem) {
        play_layout?.visibility = if (item.manga.unread > 0 && item.unreadType > 0 && !item.hideReadingButton)
            View.VISIBLE else View.GONE
    }

    /**
     * Called when an item is released.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        (adapter as? LibraryCategoryAdapter)?.libraryListener?.onItemReleased(position)
    }

    override fun onLongClick(view: View?): Boolean {
        return if (adapter.isLongPressDragEnabled) {
            super.onLongClick(view)
            false
        } else super.onLongClick(view)
    }
}
