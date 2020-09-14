package eu.kanade.tachiyomi.ui.manga

import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class MangaDetailsAdapter(
    val controller: MangaDetailsController
) : BaseChapterAdapter<IFlexible<*>>(controller) {

    val preferences: PreferencesHelper by injectLazy()

    val hasShownSwipeTut
        get() = preferences.shownChapterSwipeTutorial()

    var items: List<ChapterItem> = emptyList()

    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' }
    )

    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        performFilter()
    }

    fun indexOf(item: ChapterItem): Int {
        return items.indexOf(item)
    }

    fun indexOf(chapterId: Long): Int {
        return currentItems.indexOfFirst { it is ChapterItem && it.id == chapterId }
    }

    fun performFilter() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            updateDataSet(items)
        } else {
            updateDataSet(
                items.filter {
                    it.name.contains(s, true) ||
                        it.scanlator?.contains(s, true) == true
                }
            )
        }
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.RIGHT -> controller.bookmarkChapter(position)
            ItemTouchHelper.LEFT -> controller.toggleReadChapter(position)
        }
    }

    override fun onCreateBubbleText(position: Int): String {
        val chapter =
            getItem(position) as? ChapterItem ?: return recyclerView.context.getString(R.string.top)
        return when (val scrollType = presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS -> {
                val volume = ChapterUtil.getGroupNumber(chapter)
                if (volume != null) {
                    recyclerView.context.getString(
                        if (scrollType == MangaDetailsPresenter.MULTIPLE_SEASONS) R.string.season_
                        else R.string.volume_,
                        volume
                    )
                } else {
                    getChapterName(chapter)
                }
            }
            MangaDetailsPresenter.TENS_OF_CHAPTERS -> recyclerView.context.getString(
                R.string.chapters_,
                get10sRange(
                    chapter.chapter_number
                )
            )
            else -> getChapterName(chapter)
        }
    }

    private fun getChapterName(item: ChapterItem): String {
        return if (item.chapter_number > 0) {
            recyclerView.context.getString(
                R.string.chapter_,
                decimalFormat.format(item.chapter_number)
            )
        } else {
            item.name
        }
    }

    private fun get10sRange(value: Float): String {
        val number = value.toInt()
        return if (number < 10) "0-9"
        else {
            val hundred = number / 10
            "${hundred}0-${hundred + 1}9"
        }
    }

    interface MangaDetailsInterface : MangaHeaderInterface, DownloadInterface

    interface MangaHeaderInterface {
        fun coverColor(): Int?
        fun mangaPresenter(): MangaDetailsPresenter
        fun prepareToShareManga()
        fun openInWebView()
        fun startDownloadRange(position: Int)
        fun readNextChapter()
        fun topCoverHeight(): Int
        fun tagClicked(text: String)
        fun globalSearch(text: String)
        fun showChapterFilter()
        fun favoriteManga(longPress: Boolean)
        fun copyToClipboard(content: String, label: Int)
        fun zoomImageFromThumb(thumbView: View)
        fun showTrackingSheet()
        fun updateScroll()
        fun setFavButtonPopup(popupView: View)
    }
}
