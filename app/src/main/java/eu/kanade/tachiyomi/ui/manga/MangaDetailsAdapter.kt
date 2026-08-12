package eu.kanade.tachiyomi.ui.manga

import android.view.ActionMode
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.MissingChaptersItem
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterDifference
import eu.kanade.tachiyomi.ui.reader.viewer.isChapterNumberOutlier
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.isLTR
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.floor

/** The cover-derived colors used to theme the manga details page, computed together so every consumer stays in sync */
data class MangaDetailsColors(
    val cover: Int? = null,
    val accent: Int? = null,
    val background: Int? = null,
)

class MangaDetailsAdapter(
    val controller: MangaDetailsController,
) : BaseChapterAdapter<IFlexible<*>>(controller) {
    val preferences: PreferencesHelper by injectLazy()

    val hasShownSwipeTut
        get() = preferences.shownChapterSwipeTutorial()

    var items: List<ChapterItem> = emptyList()

    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter

    val decimalFormat =
        DecimalFormat(
            "#.###",
            DecimalFormatSymbols()
                .apply { decimalSeparator = '.' },
        )

    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        performFilter()
    }

    fun indexOf(item: ChapterItem): Int = items.indexOf(item)

    fun indexOf(chapterId: Long): Int = currentItems.indexOfFirst { it is ChapterItem && it.id == chapterId }

    fun performFilter() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            updateDataSet(insertMissingChapterItems(items))
        } else {
            updateDataSet(
                items.filter {
                    it.name.contains(s, true) ||
                        it.scanlator?.contains(s, true) == true
                },
            )
        }
    }

    private fun insertMissingChapterItems(chapters: List<ChapterItem>): List<IFlexible<*>> {
        if (chapters.size < 2 || !preferences.showChapterMissingWarnings().get()) return chapters

        val descending = presenter.sortDescending()
        val result = ArrayList<IFlexible<*>>(chapters.size + 1)
        var anchor = chapters.first()
        result.add(anchor)
        var i = 1
        while (i < chapters.size) {
            val current = chapters[i]
            val next = chapters.getOrNull(i + 1)
            val isOutlier = next != null && isChapterNumberOutlier(anchor.chapter, current.chapter, next.chapter)
            if (!isOutlier) {
                val (higher, lower) = if (descending) anchor to current else current to anchor
                val gap = calculateChapterDifference(higher.chapter, lower.chapter).toInt()
                if (gap > 0) {
                    val startChapter = floor(lower.chapter.chapter_number).toInt() + 1
                    val endChapter = floor(higher.chapter.chapter_number).toInt() - 1
                    result.add(
                        MissingChaptersItem("${lower.chapter.id}-${higher.chapter.id}", gap, startChapter, endChapter),
                    )
                }
                anchor = current
            }
            result.add(current)
            i++
        }

        // The oldest chapter (last in display order when descending, first when ascending) may not
        // start at 1 if the earliest chapters aren't available from the source.
        val oldestChapter = if (descending) anchor else chapters.first()
        val missingFromStart = floor(oldestChapter.chapter.chapter_number).toInt().minus(1).coerceAtLeast(0)
        if (missingFromStart > 0) {
            val startItem = MissingChaptersItem("start-${oldestChapter.chapter.id}", missingFromStart, 1, missingFromStart)
            if (descending) result.add(startItem) else result.add(0, startItem)
        }
        return result
    }

    override fun onItemSwiped(
        position: Int,
        direction: Int,
    ) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.RIGHT ->
                if (recyclerView.resources.isLTR) {
                    controller.bookmarkChapter(position)
                } else {
                    controller.toggleReadChapter(position)
                }
            ItemTouchHelper.LEFT ->
                if (recyclerView.resources.isLTR) {
                    controller.toggleReadChapter(position)
                } else {
                    controller.bookmarkChapter(position)
                }
        }
    }

    override fun onCreateBubbleText(position: Int): String {
        val item = getItem(position)
        if (item is MissingChaptersItem) {
            return missingChaptersRangeText(item)
        }
        val chapter = item as? ChapterItem ?: return recyclerView.context.getString(R.string.top)
        return when (val scrollType = presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS -> {
                val volume = ChapterUtil.getGroupNumber(chapter)
                if (volume != null) {
                    recyclerView.context.getString(
                        if (scrollType == MangaDetailsPresenter.MULTIPLE_SEASONS) {
                            R.string.season_
                        } else {
                            R.string.volume_
                        },
                        volume,
                    )
                } else {
                    getChapterName(chapter)
                }
            }
            MangaDetailsPresenter.TENS_OF_CHAPTERS ->
                recyclerView.context.getString(
                    R.string.chapters_,
                    get10sRange(chapter.chapter_number),
                )
            else -> getChapterName(chapter)
        }
    }

    private fun missingChaptersRangeText(item: MissingChaptersItem): String {
        val range =
            if (item.startChapter >= item.endChapter) {
                item.startChapter.toString()
            } else {
                "${item.startChapter}-${item.endChapter}"
            }
        return recyclerView.context.getString(R.string.missing_chapters_range, range)
    }

    private fun getChapterName(item: ChapterItem): String =
        if (item.chapter_number > 0) {
            recyclerView.context.getString(
                R.string.chapter_,
                decimalFormat.format(item.chapter_number),
            )
        } else {
            item.name
        }

    private fun get10sRange(value: Float): String {
        val number = value.toInt()
        return if (number < 10) {
            "0-9"
        } else {
            val hundred = number / 10
            "${hundred}0-${hundred + 1}9"
        }
    }

    interface MangaDetailsInterface :
        MangaHeaderInterface,
        DownloadInterface

    interface MangaHeaderInterface {
        fun themeColors(): MangaDetailsColors

        fun mangaPresenter(): MangaDetailsPresenter

        fun prepareToShareManga()

        fun openInWebView()

        fun startDownloadRange(position: Int)

        fun readNextChapter(readingButton: View)

        fun topCoverHeight(): Int

        fun showFloatingActionMode(
            view: TextView,
            content: String? = null,
            isTag: Boolean = false,
        )

        fun showChapterFilter()

        fun favoriteManga(longPress: Boolean)

        fun copyContentToClipboard(
            content: String,
            label: Int,
            useToast: Boolean = false,
        )

        fun customActionMode(view: TextView): ActionMode.Callback

        fun copyContentToClipboard(
            content: String,
            label: String?,
            useToast: Boolean = false,
        )

        fun zoomImageFromThumb(thumbView: View)

        fun showCoverContextMenu(view: View)

        fun showTrackingSheet()

        fun updateScroll()

        fun setFavButtonPopup(popupView: View)
    }
}
