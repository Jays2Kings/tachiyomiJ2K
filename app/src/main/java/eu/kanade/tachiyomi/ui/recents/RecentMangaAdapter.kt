package eu.kanade.tachiyomi.ui.recents

import androidx.recyclerview.widget.ItemTouchHelper
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class RecentMangaAdapter(val delegate: RecentsInterface) :
    BaseChapterAdapter<IFlexible<*>>(delegate) {

    init {
        setDisplayHeadersAtStartUp(true)
    }

    fun updateItems(items: List<IFlexible<*>>?) {
        updateDataSet(items)
    }

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' }
    )

    interface RecentsInterface : RecentMangaInterface, DownloadInterface

    interface RecentMangaInterface {
        fun onCoverClick(position: Int)
        fun markAsRead(position: Int)
        fun isSearching(): Boolean
        fun showHistory()
        fun showUpdates()
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.LEFT -> delegate.markAsRead(position)
        }
    }
}
