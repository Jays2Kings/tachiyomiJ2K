package eu.kanade.tachiyomi.ui.manga

import android.content.Context
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
    val controller: MangaDetailsController,
    context: Context
) : BaseChapterAdapter<IFlexible<*>>(controller) {

    val preferences: PreferencesHelper by injectLazy()

    var items: List<ChapterItem> = emptyList()

    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter

    val readColor = ChapterUtil.readColor(context)

    val unreadColor = ChapterUtil.unreadColor(context)

    val bookmarkedColor = ChapterUtil.bookmarkedColor(context)

    val bookmarkedAndReadColor = ChapterUtil.bookmarkedAndReadColor(context)

    val decimalFormat = DecimalFormat("#.###", DecimalFormatSymbols()
            .apply { decimalSeparator = '.' })

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
            updateDataSet(items.filter { it.name.contains(s, true) ||
                it.scanlator?.contains(s, true) == true })
        }
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.RIGHT -> controller.bookmarkChapter(position)
            ItemTouchHelper.LEFT -> controller.toggleReadChapter(position)
        }
    }

    fun getSectionText(position: Int): String? {
        val chapter = getItem(position) as? ChapterItem ?: return null
        if (position == itemCount - 1) return "-"
        return when (presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS ->
                presenter.getGroupNumber(chapter)?.toString() ?: "*"
            MangaDetailsPresenter.HUNDREDS_OF_CHAPTERS ->
                if (chapter.chapter_number < 0) "*"
            else (chapter.chapter_number / 100).toInt().toString()
            MangaDetailsPresenter.TENS_OF_CHAPTERS ->
                if (chapter.chapter_number < 0) "*"
                else (chapter.chapter_number / 10).toInt().toString()
            else -> null
        }
    }

    fun getFullText(position: Int): String {
        val chapter =
            getItem(position) as? ChapterItem ?: return recyclerView.context.getString(R.string.top)
        if (position == itemCount - 1) return recyclerView.context.getString(R.string.bottom)
        return when (val scrollType = presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS -> {
                val volume = presenter.getGroupNumber(chapter)
                if (volume != null) recyclerView.context.getString(
                    if (scrollType == MangaDetailsPresenter.MULTIPLE_SEASONS) R.string.season_
                    else R.string.volume_, volume)
                else recyclerView.context.getString(R.string.unknown)
            }
            MangaDetailsPresenter.HUNDREDS_OF_CHAPTERS -> recyclerView.context.getString(
                R.string.chapters_, get100sRange(
                    chapter.chapter_number
                )
            )
            MangaDetailsPresenter.TENS_OF_CHAPTERS -> recyclerView.context.getString(
                R.string.chapters_, get10sRange(
                    chapter.chapter_number
                )
            )
            else -> recyclerView.context.getString(R.string.unknown)
        }
    }

    private fun get100sRange(value: Float): String {
        val number = value.toInt()
        return if (number < 100) "0-99"
        else {
            val hundred = number / 100
            "${hundred}00-${hundred}99"
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
        fun showChapterFilter()
        fun favoriteManga(longPress: Boolean)
        fun copyToClipboard(content: String, label: Int)
        fun zoomImageFromThumb(thumbView: View)
        fun showTrackingSheet()
    }
}
