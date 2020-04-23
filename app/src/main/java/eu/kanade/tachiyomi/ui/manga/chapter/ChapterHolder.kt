package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.chapters_item.*
import kotlinx.android.synthetic.main.download_button.*

class ChapterHolder(
    view: View,
    private val adapter: MangaDetailsAdapter
) : BaseChapterHolder(view, adapter) {

    private var localSource = false

    init {
        download_button.setOnLongClickListener {
            adapter.delegate.startDownloadRange(adapterPosition)
            true
        }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter
        val isLocked = item.isLocked
        chapter_title.text = when (manga.displayMode) {
            Manga.DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.chapter_, number)
            }
            else -> chapter.name
        }

        localSource = manga.source == LocalSource.ID
        download_button.visibleIf(!localSource && !isLocked)

        var chapterColor = when {
            isLocked -> adapter.unreadColor
            chapter.bookmark && chapter.read -> adapter.bookmarkedAndReadColor
            chapter.bookmark -> adapter.bookmarkedColor
            chapter.read -> adapter.readColor
            else -> adapter.unreadColor
        }

        // Set correct text color
        chapter_title.setTextColor(chapterColor)

        val statuses = mutableListOf<String>()

        ChapterUtil.relativeDate(chapter)?.let { statuses.add(it) }

        val showPagesLeft = !chapter.read && chapter.last_page_read > 0 && !isLocked

        if (showPagesLeft && chapter.pages_left > 0) {
            statuses.add(
                itemView.resources.getQuantityString(
                    R.plurals.pages_left, chapter.pages_left, chapter.pages_left
                )
            )
        } else if (showPagesLeft) {
            statuses.add(
                itemView.context.getString(
                    R.string.page_, chapter.last_page_read + 1
                )
            )
        }

        chapter.scanlator?.isNotBlank()?.let { statuses.add(chapter.scanlator!!) }

        if (front_view.translationX == 0f) {
            read.setImageDrawable(
                read.context.contextCompatDrawable(
                    when (item.read) {
                        true -> R.drawable.ic_eye_off_24dp
                        false -> R.drawable.ic_eye_24dp
                    }
                )
            )
            bookmark.setImageDrawable(
                read.context.contextCompatDrawable(
                    when (item.bookmark) {
                        true -> R.drawable.ic_bookmark_off_24dp
                        false -> R.drawable.ic_bookmark_24dp
                    }
                )
            )
        }
        // this will color the scanlator the same bookmarks
        chapter_scanlator.setTextColor(chapterColor)
        chapter_scanlator.text = statuses.joinToString(" • ")

        val status = when (adapter.isSelected(adapterPosition)) {
            true -> Download.CHECKED
            false -> item.status
        }

        notifyStatus(status, item.isLocked, item.progress)
        resetFrontView()
    }

    override fun getFrontView(): View {
        return front_view
    }

    override fun getRearRightView(): View {
        return right_view
    }

    override fun getRearLeftView(): View {
        return left_view
    }

    private fun resetFrontView() {
        if (front_view.translationX != 0f) itemView.post { adapter.notifyItemChanged(adapterPosition) }
    }

    fun notifyStatus(status: Int, locked: Boolean, progress: Int) = with(download_button) {
        if (locked) {
            gone()
            return
        }
        download_button.visibleIf(!localSource)
        setDownloadStatus(status, progress)
    }
}
