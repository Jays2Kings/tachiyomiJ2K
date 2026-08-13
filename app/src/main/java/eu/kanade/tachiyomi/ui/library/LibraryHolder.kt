package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setCards
import kotlin.math.floor

/**
 * Generic class used to hold the displayed data of a manga in the library.
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @param listener a listener to react to the single tap and long tap events.
 */

abstract class LibraryHolder(
    view: View,
    val adapter: LibraryCategoryAdapter,
) : BaseFlexibleViewHolder(view, adapter) {
    protected val color = ColorUtils.setAlphaComponent(itemView.context.getResourceColor(R.attr.colorPrimary), 75)

    init {
        val card = itemView.findViewById<MaterialCardView>(R.id.card)
        val badgeView = itemView.findViewById<LibraryBadge>(R.id.unread_download_badge)
        if (card != null && badgeView != null) {
            setCards(adapter.showOutline, card, badgeView)
        }
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    abstract fun onSetValues(item: LibraryItem)

    fun setUnreadBadge(
        badge: LibraryBadge,
        item: LibraryItem,
    ) {
        val showTotal = item.header.category.sortingMode() == LibrarySort.TotalChapters
        badge.setUnreadDownload(
            when {
                showTotal -> item.manga.totalChapters
                item.unreadType == 2 -> item.manga.unread
                item.unreadType == 1 -> if (item.manga.unread > 0) -1 else -2
                else -> -2
            },
            when {
                item.downloadCount == -1 -> -1
                item.manga.isLocal() -> -2
                else -> item.downloadCount
            },
            showTotal,
            item.sourceLanguage,
            this is LibraryGridHolder,
            item.latestChapterNumber
                .takeIf { it > 0f }
                ?.let { floor(it).toInt() },
        )
    }

    fun setReadingButton(item: LibraryItem) {
        itemView.findViewById<View>(R.id.play_layout)?.isVisible =
            item.manga.unread > 0 &&
            !item.hideReadingButton
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

    override fun onLongClick(view: View?): Boolean =
        if (adapter.isLongPressDragEnabled) {
            val manga = (adapter.getItem(flexibleAdapterPosition) as LibraryItem).manga
            if (!isDraggable && !manga.isBlank() && !manga.isHidden()) {
                adapter.mItemLongClickListener.onItemLongClick(flexibleAdapterPosition)
                toggleActivation()
                true
            } else {
                super.onLongClick(view)
                false
            }
        } else {
            super.onLongClick(view)
        }
}
